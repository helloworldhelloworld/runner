#!/usr/bin/env python3
"""Freeze a trusted workspace and evaluate native Maven scenarios from that copy."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import shlex
import signal
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

SCRIPT = 'scripts/improvement_loop.py'


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def sha(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + '\n', encoding='utf-8')


def safe_path(root, name):
    parts = PurePosixPath(name).parts
    if not parts or PurePosixPath(name).is_absolute() or '..' in parts or '\\' in name:
        raise ValueError('Invalid relative path: ' + name)
    path = root.joinpath(*parts)
    for node in [path, *path.parents]:
        if node == root:
            break
        if node.is_symlink():
            raise ValueError('Symlink is not a candidate input: ' + name)
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Path escapes bundle: ' + name)
    return path


def source_path(name):
    parts = PurePosixPath(name).parts
    return not any(p in ('.git', 'target', '__pycache__') for p in parts) and parts[0] not in ('ci-results', '.worktrees')


def file_record(root, name):
    path = safe_path(root, name)
    if not path.is_file():
        raise ValueError('Missing input: ' + name)
    return {'path': name, 'sha256': sha(path), 'size': path.stat().st_size,
            'executable': bool(path.stat().st_mode & 0o111)}


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args])


def workspace_files(repo):
    names = git(repo, 'ls-files', '--cached', '--others', '--exclude-standard', '-z').decode().split('\0')
    result = []
    for name in sorted(set(n for n in names if n and source_path(n))):
        path = safe_path(repo, name)
        if path.exists():  # A tracked deletion is represented by absence in the snapshot.
            result.append(file_record(repo, name))
    return result


def candidate_digest(files):
    return digest({'schema': 'runner.candidate.v1', 'files': files})


def validate_spec(spec):
    required = {'schemaVersion', 'id', 'module', 'tests', 'timeoutSeconds', 'requiredEvidence', 'feedback', 'changeScope'}
    if not isinstance(spec, dict) or set(spec) != required or spec['schemaVersion'] != 1:
        raise ValueError('Unsupported evaluation definition')
    for field in ('id', 'module'):
        if not isinstance(spec[field], str) or not re.fullmatch(r'[a-zA-Z0-9_-]+', spec[field]):
            raise ValueError('Invalid ' + field)
    tests = spec['tests']
    if not isinstance(tests, list) or not tests or any(not isinstance(t, str) or not re.fullmatch(r'[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*', t) for t in tests) or len(tests) != len(set(tests)):
        raise ValueError('Explicit unique test classes are required')
    if type(spec['timeoutSeconds']) is not int or not 1 <= spec['timeoutSeconds'] <= 3600:
        raise ValueError('timeoutSeconds must be between 1 and 3600')
    evidence = spec['requiredEvidence']
    if not isinstance(evidence, list) or not evidence:
        raise ValueError('At least one downstream evidence file is required')
    for item in evidence:
        if not isinstance(item, dict) or set(item) != {'file', 'caseId'} or not re.fullmatch(r'[\w-]+\.json', item['file']) or not re.fullmatch(r'[\w-]+', item['caseId']):
            raise ValueError('Invalid evidence definition')
    if len({e['file'] for e in evidence}) != len(evidence) or len({e['caseId'] for e in evidence}) != len(evidence):
        raise ValueError('Duplicate evidence identity')
    if not isinstance(spec['changeScope'], list) or not spec['changeScope'] or any(not isinstance(s, str) or not s for s in spec['changeScope']):
        raise ValueError('Explicit changeScope is required')
    if not isinstance(spec['feedback'], list) or not spec['feedback']:
        raise ValueError('Feedback is required')
    for item in spec['feedback']:
        if not isinstance(item, dict) or set(item) != {'source', 'observation', 'expected'} or any(not isinstance(v, str) or not v for v in item.values()):
            raise ValueError('Invalid feedback')


def evaluation_inputs(files, spec_path):
    return [f for f in files if '/src/test/' in f['path'] or f['path'] in (spec_path, SCRIPT)]


def evaluation_digest(evaluation):
    return digest({k: evaluation[k] for k in ('schema', 'specPath', 'spec', 'inputs')})


def capture(repo, spec_name, output):
    repo = repo.resolve()
    output = output.absolute()
    if output.exists() or output.is_symlink():
        raise ValueError('Output already exists')
    if output.resolve().is_relative_to(repo):
        raise ValueError('Output must be outside the source repository')
    if Path(git(repo, 'rev-parse', '--show-toplevel').decode().strip()).resolve() != repo:
        raise ValueError('--repo must be the Git root')
    spec_file = safe_path(repo, spec_name)
    spec = read_json(spec_file)
    validate_spec(spec)
    files = workspace_files(repo)
    names = {f['path'] for f in files}
    if not {spec_name, SCRIPT, 'pom.xml', spec['module'] + '/pom.xml'} <= names:
        raise ValueError('Definition, runner script and Maven POMs must be captured inputs')
    if sha(repo / SCRIPT) != sha(Path(__file__)):
        raise ValueError('Capture must use the source repository execution script')
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix='.candidate-', dir=output.parent))
    try:
        snapshot = staging / 'snapshot'
        snapshot.mkdir()
        for entry in files:
            target = safe_path(snapshot, entry['path'])
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(safe_path(repo, entry['path']), target)
            target.chmod(0o755 if entry['executable'] else 0o644)
            if file_record(snapshot, entry['path']) != entry:
                raise ValueError('Workspace changed during capture')
        if workspace_files(repo) != files:
            raise ValueError('Workspace changed during capture')
        candidate = {'schema': 'runner.candidate.v1', 'digest': candidate_digest(files), 'files': files,
                     'source': {'head': git(repo, 'rev-parse', 'HEAD').decode().strip()}}
        evaluation = {'schema': 'runner.evaluation.v1', 'specPath': spec_name, 'spec': spec,
                      'inputs': evaluation_inputs(files, spec_name)}
        evaluation['digest'] = evaluation_digest(evaluation)
        write_json(staging / 'candidate.json', candidate)
        write_json(staging / 'evaluation.json', evaluation)
        staging.rename(output)
        return candidate
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def verify_inputs(bundle):
    candidate = read_json(bundle / 'candidate.json')
    evaluation = read_json(bundle / 'evaluation.json')
    if candidate.get('schema') != 'runner.candidate.v1' or candidate_digest(candidate['files']) != candidate['digest']:
        raise ValueError('Candidate manifest digest mismatch')
    snapshot = bundle / 'snapshot'
    actual_names = sorted(p.relative_to(snapshot).as_posix() for p in snapshot.rglob('*')
                          if (p.is_file() or p.is_symlink()) and source_path(p.relative_to(snapshot).as_posix()))
    if actual_names != [f['path'] for f in candidate['files']]:
        raise ValueError('Snapshot input set changed')
    for entry in candidate['files']:
        if file_record(snapshot, entry['path']) != entry:
            raise ValueError('Snapshot content changed: ' + entry['path'])
    validate_spec(evaluation['spec'])
    if evaluation.get('schema') != 'runner.evaluation.v1' or evaluation_digest(evaluation) != evaluation['digest']:
        raise ValueError('Evaluation digest mismatch')
    if evaluation['inputs'] != evaluation_inputs(candidate['files'], evaluation['specPath']):
        raise ValueError('Evaluation inputs do not match candidate')
    if read_json(safe_path(snapshot, evaluation['specPath'])) != evaluation['spec']:
        raise ValueError('Evaluation definition differs from snapshot')
    if sha(snapshot / SCRIPT) != sha(Path(__file__)):
        raise ValueError('Execution script changed; use the frozen script or capture again')
    return candidate, evaluation


def junit_results(snapshot):
    cases, properties = [], []
    for path in sorted(snapshot.glob('*/target/surefire-reports/TEST-*.xml')):
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
        for suite in suites:
            for case in suite.findall('testcase'):
                problem = next((child for child in case if child.tag in ('failure', 'error', 'skipped')), None)
                cases.append({'class': case.get('classname', suite.get('name', '')), 'name': case.get('name', ''),
                              'status': problem.tag if problem is not None else 'passed',
                              'detail': (problem.get('message', '') + '\n' + (problem.text or '')) if problem is not None else ''})
            properties.append({p.get('name'): p.get('value') for p in suite.findall('properties/property')})
    return cases, properties


def assess(spec, cases, exit_code, evidence_errors):
    reasons = list(evidence_errors)
    if exit_code != 0:
        reasons.append('Maven did not complete successfully: ' + str(exit_code))
    for required in spec['tests']:
        selected = [c for c in cases if c['class'] == required or ('.' not in required and c['class'].rsplit('.', 1)[-1] == required)]
        if not selected:
            reasons.append('Required test did not execute: ' + required)
        elif any(c['status'] != 'passed' for c in selected):
            reasons.append('Required test failed, errored or skipped: ' + required)
    if any(c['status'] != 'passed' for c in cases):
        reasons.append('At least one executed case did not pass')
    return reasons


def evidence_errors(bundle, candidate, evaluation):
    errors = []
    for required in evaluation['spec']['requiredEvidence']:
        path = safe_path(bundle / 'evidence', required['file'])
        if not path.is_file():
            errors.append('Missing downstream evidence: ' + required['file'])
            continue
        try:
            data = read_json(path)
            if data.get('schemaVersion') != 1 or data.get('caseId') != required['caseId'] or data.get('candidateDigest') != candidate['digest'] or data.get('evaluationDigest') != evaluation['digest'] or not isinstance(data.get('observations'), dict) or not data['observations']:
                errors.append('Evidence binding/content mismatch: ' + required['file'])
        except (ValueError, AttributeError):
            errors.append('Invalid evidence JSON: ' + required['file'])
    return errors


def environment(properties):
    result = {'python': sys.version.split()[0], 'platform': sys.platform, 'java': [], 'classpath': []}
    java_seen, jars = set(), set()
    for props in properties:
        java = {k: props.get(k, '') for k in ('java.version', 'java.vendor', 'os.name', 'os.arch')}
        key = canonical(java)
        if key not in java_seen:
            result['java'].append(java)
            java_seen.add(key)
        for key in ('java.class.path', 'surefire.test.class.path'):
            for item in props.get(key, '').split(os.pathsep):
                path = Path(item)
                if item and path.is_file() and path.suffix == '.jar' and str(path) not in jars:
                    jars.add(str(path))
                    result['classpath'].append({'name': path.name, 'sha256': sha(path)})
    result['classpath'].sort(key=lambda entry: (entry['name'], entry['sha256']))
    return result


def output_records(bundle):
    paths = [bundle / 'candidate.json', bundle / 'evaluation.json', bundle / 'build.log', bundle / 'handoff.md']
    paths += list((bundle / 'evidence').glob('*.json'))
    paths += list((bundle / 'snapshot').glob('*/target/surefire-reports/TEST-*.xml'))
    paths += list((bundle / 'snapshot').glob('*/target/classes/**/*'))
    paths += list((bundle / 'snapshot').glob('*/target/test-classes/**/*'))
    return [file_record(bundle, p.relative_to(bundle).as_posix()) for p in sorted(set(paths)) if p.is_file()]


def write_handoff(bundle, report, spec):
    lines = ['# Runner candidate handoff', '', 'Result: ' + report['status'],
             'Candidate: ' + report['candidateDigest'], 'Evaluation: ' + report['evaluationDigest'], '',
             'Scope: controlled native acceptance; no production activation or live quality claim.', '',
             '## Feedback', '']
    for item in spec['feedback']:
        lines.extend(['- Source: ' + item['source'], '  Observed: ' + item['observation'], '  Expected: ' + item['expected']])
    lines.extend(['', '## Change scope', '', *['- ' + item for item in spec['changeScope']], '', '## Findings', ''])
    lines += ['- ' + item for item in report['reasons']] or ['- All declared cases and evidence requirements passed.']
    replay_command = [sys.executable, str(bundle / 'snapshot' / SCRIPT), 'replay', '--bundle', str(bundle),
                      '--output', str(bundle.with_name(bundle.name + '-replay'))]
    lines.extend(['', 'Inspect report.json, evidence/ and build.log before changing the candidate.',
                  'Replay the frozen candidate into a new destination:', '', '```bash',
                  shlex.join(replay_command), '```', '',
                  'Capture a new bundle after changing source or evaluation criteria; keep this result as history.',
                  'Rollback: discard the candidate; this workflow does not change an active deployment.', ''])
    (bundle / 'handoff.md').write_text('\n'.join(lines), encoding='utf-8')


def evaluate(bundle):
    candidate, evaluation = verify_inputs(bundle)
    if (bundle / 'report.json').exists() or (bundle / 'build.log').exists():
        raise ValueError('Bundle already evaluated or incomplete; capture a new bundle')
    spec = evaluation['spec']
    evidence = bundle / 'evidence'
    evidence.mkdir()
    command = ['mvn', '--show-version', '--batch-mode', '--no-transfer-progress', '-pl', spec['module'], '-am', 'clean', 'test',
               '-Dtest=' + ','.join(spec['tests']), '-Dsurefire.failIfNoSpecifiedTests=false',
               '-Drunner.evidence.dir=' + str(evidence), '-Drunner.candidate.digest=' + candidate['digest'],
               '-Drunner.evaluation.digest=' + evaluation['digest']]
    started = datetime.now(timezone.utc).isoformat()
    run_error = None
    maven_version = ''
    with (bundle / 'build.log').open('wb') as log:
        try:
            with subprocess.Popen(command, cwd=bundle / 'snapshot', stdout=log, stderr=subprocess.STDOUT,
                                  start_new_session=True) as process:
                try:
                    code = process.wait(timeout=spec['timeoutSeconds'])
                except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                    code = 124 if isinstance(error, subprocess.TimeoutExpired) else 130
                    run_error = 'Execution timed out' if code == 124 else 'Execution interrupted'
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            code = 127
            run_error = str(error)
    with (bundle / 'build.log').open(encoding='utf-8', errors='replace') as log:
        maven_version = ''.join(next(log, '') for _ in range(5)).strip()
    errors = [run_error] if run_error else []
    try:
        verify_inputs(bundle)
    except (ValueError, OSError) as error:
        errors.append('Inputs changed during execution: ' + str(error))
    errors += evidence_errors(bundle, candidate, evaluation)
    try:
        cases, props = junit_results(bundle / 'snapshot')
    except (ET.ParseError, OSError) as error:
        cases, props = [], []
        errors.append('Invalid JUnit report: ' + str(error))
    reasons = assess(spec, cases, code, errors)
    report = {'schema': 'runner.report.v1', 'candidateDigest': candidate['digest'],
              'evaluationDigest': evaluation['digest'], 'startedAt': started,
              'finishedAt': datetime.now(timezone.utc).isoformat(), 'command': command,
              'exitCode': code, 'status': 'passed' if not reasons else 'failed', 'reasons': reasons,
              'cases': cases, 'environment': dict(environment(props), mavenVersion=maven_version)}
    write_handoff(bundle, report, spec)
    report['outputs'] = output_records(bundle)
    report['digest'] = digest(report)
    write_json(bundle / 'report.json', report)
    return report


def verify(bundle, repo=None):
    candidate, evaluation = verify_inputs(bundle)
    if repo is not None and workspace_files(repo.resolve()) != candidate['files']:
        raise ValueError('Current workspace differs from the tested candidate')
    if not (bundle / 'report.json').exists():
        if (bundle / 'build.log').exists():
            raise ValueError('Incomplete evaluation without a report')
        return {'status': 'captured', 'candidateDigest': candidate['digest']}
    report = read_json(bundle / 'report.json')
    stored = report.pop('digest')
    if report.get('schema') != 'runner.report.v1' or digest(report) != stored:
        raise ValueError('Report digest mismatch')
    if report['candidateDigest'] != candidate['digest'] or report['evaluationDigest'] != evaluation['digest']:
        raise ValueError('Report belongs to a different candidate/evaluation')
    if output_records(bundle) != report['outputs']:
        raise ValueError('Recorded outputs changed or disappeared')
    if report['status'] == 'passed' and assess(evaluation['spec'], report['cases'], report['exitCode'], evidence_errors(bundle, candidate, evaluation)):
        raise ValueError('Passing report does not satisfy the evaluation')
    return {'status': report['status'], 'candidateDigest': candidate['digest']}


def replay(bundle, output):
    verify(bundle)  # Failed evaluations can be reproduced, but altered evidence cannot.
    candidate, evaluation = verify_inputs(bundle)
    output = output.absolute()
    if output.exists() or output.is_symlink():
        raise ValueError('Output already exists')
    if output.resolve().is_relative_to(bundle.resolve()):
        raise ValueError('Replay output must be outside the original bundle')
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix='.replay-', dir=output.parent))
    try:
        snapshot = staging / 'snapshot'
        snapshot.mkdir()
        for entry in candidate['files']:
            target = safe_path(snapshot, entry['path'])
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(safe_path(bundle / 'snapshot', entry['path']), target)
            target.chmod(0o755 if entry['executable'] else 0o644)
            if file_record(snapshot, entry['path']) != entry:
                raise ValueError('Source bundle changed during replay')
        write_json(staging / 'candidate.json', candidate)
        write_json(staging / 'evaluation.json', evaluation)
        verify_inputs(staging)
        staging.rename(output)
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    return evaluate(output.resolve())


def compare(baseline, candidate, output):
    baseline, candidate, output = baseline.resolve(), candidate.resolve(), output.absolute()
    if output.exists() or output.is_symlink():
        raise ValueError('Output already exists')
    if any(output.resolve().is_relative_to(bundle) for bundle in (baseline, candidate)):
        raise ValueError('Comparison output must be outside the input bundles')
    reports, definitions = [], []
    for bundle in (baseline, candidate):
        if verify(bundle)['status'] == 'captured':
            raise ValueError('Both candidates must have executed reports')
        reports.append(read_json(bundle / 'report.json'))
        definitions.append(read_json(bundle / 'evaluation.json'))
    if reports[0]['evaluationDigest'] != reports[1]['evaluationDigest']:
        raise ValueError('Candidates use different evaluation content; rerun both under the same criteria')

    def indexed(report):
        result = {}
        for case in report['cases']:
            key = (case['class'], case['name'])
            if any(not isinstance(value, str) or not value for value in key):
                raise ValueError('Missing test identity')
            if key in result or case['status'] not in ('passed', 'failure', 'error', 'skipped'):
                raise ValueError('Duplicate test identity or invalid case status')
            result[key] = case['status']
        return result

    before, after = (indexed(report) for report in reports)
    cases = []
    for cls, name in sorted(before.keys() | after.keys()):
        key = (cls, name)
        old, new = before.get(key), after.get(key)
        if old is None:
            change = 'added'
        elif new is None:
            change = 'removed'
        elif old == 'passed' and new != 'passed':
            change = 'regressed'
        elif old != 'passed' and new == 'passed':
            change = 'fixed'
        else:
            change = 'unchanged'
        cases.append({'class': cls, 'name': name, 'before': old, 'after': new, 'change': change})
    changes = {case['change'] for case in cases}
    if not cases or changes & {'added', 'removed'}:
        status = 'incomparable'
    elif 'regressed' in changes or reports[0]['status'] == 'passed' and reports[1]['status'] != 'passed':
        status = 'regressed'
    elif 'fixed' in changes and reports[1]['status'] == 'passed':
        status = 'improved'
    elif reports[1]['status'] == 'passed':
        status = 'no_regression_observed'
    else:
        status = 'unresolved'

    def reference(bundle, report):
        return {'bundle': str(bundle), 'candidateDigest': report['candidateDigest'],
                'reportDigest': report['digest'], 'status': report['status'],
                'evidence': str(bundle / 'evidence') + '/'}

    result = {'schema': 'runner.comparison.v1', 'status': status,
              'evaluationDigest': reports[0]['evaluationDigest'],
              'baseline': reference(baseline, reports[0]), 'candidate': reference(candidate, reports[1]),
              'cases': cases, 'feedback': definitions[0]['spec']['feedback']}
    lines = ['# Runner candidate comparison', '', 'Result: ' + status, '',
             'Scope: declared controlled acceptance only; no production activation or live quality claim.', '',
             'Baseline evidence: ' + result['baseline']['evidence'],
             'Candidate evidence: ' + result['candidate']['evidence'], '', '## Case findings', '']
    lines += [f"- {case['class']}#{case['name']}: {case['before']} → {case['after']} ({case['change']})"
              for case in cases]
    lines += ['', '## Original feedback', '']
    for item in result['feedback']:
        lines += ['- Source: ' + item['source'], '  Observed: ' + item['observation'],
                  '  Expected: ' + item['expected']]
    lines += ['', 'Inspect the failed payload and both evidence directories, implement a new candidate,',
              'then rerun the same evaluation. Changed criteria require rerunning both candidates.', '']
    # Refuse mixed evidence if either source changed while we were reading it.
    for bundle, report in zip((baseline, candidate), reports):
        verify(bundle)
        if read_json(bundle / 'report.json') != report:
            raise ValueError('Source report changed during comparison')
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix='.comparison-', dir=output.parent))
    try:
        (staging / 'handoff.md').write_text('\n'.join(lines), encoding='utf-8')
        result['handoffDigest'] = sha(staging / 'handoff.md')
        result['digest'] = digest(result)
        write_json(staging / 'comparison.json', result)
        staging.rename(output)
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    for name in ('capture', 'run'):
        p = sub.add_parser(name)
        p.add_argument('--repo', type=Path, required=True)
        p.add_argument('--spec', required=True)
        p.add_argument('--output', type=Path, required=True)
    for name in ('evaluate', 'verify', 'replay'):
        p = sub.add_parser(name)
        p.add_argument('--bundle', type=Path, required=True)
        if name == 'verify':
            p.add_argument('--repo', type=Path)
        if name == 'replay':
            p.add_argument('--output', type=Path, required=True)
    p = sub.add_parser('compare')
    p.add_argument('--baseline', type=Path, required=True)
    p.add_argument('--candidate', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.action == 'compare':
            result = compare(args.baseline, args.candidate, args.output)
            print(json.dumps({'status': result['status'], 'candidateDigest': result['candidate']['candidateDigest']}))
            return 0
        if args.action in ('capture', 'run'):
            candidate = capture(args.repo, args.spec, args.output)
            print('Captured candidate ' + candidate['digest'], flush=True)
            if args.action == 'capture':
                return 0
            result = evaluate(args.output.resolve())
        elif args.action == 'evaluate':
            result = evaluate(args.bundle.resolve())
        elif args.action == 'replay':
            result = replay(args.bundle.resolve(), args.output)
        else:
            result = verify(args.bundle.resolve(), args.repo)
        print(json.dumps({k: result[k] for k in ('status', 'candidateDigest')}))
        return 0 if result['status'] in ('passed', 'captured') else 1
    except (ValueError, OSError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        print('Error: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
