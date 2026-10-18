#!/usr/bin/env python3
"""Create and verify CI results that are bound to one trigger commit."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import sys
import tempfile


STAGES = ('candidate_workflow_tests', 'frozen_native_candidate', 'build_and_test')


def utc_now():
    return datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')


def validate_identity(trigger_sha, run_id):
    if not re.fullmatch(r'[0-9a-f]{40,64}', trigger_sha):
        raise ValueError('trigger SHA must be 40 or 64 lowercase hexadecimal characters')
    if not str(run_id).isdigit():
        raise ValueError('run ID must contain only digits')


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix='.' + path.name + '.', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            json.dump(value, stream, indent=2, ensure_ascii=False)
            stream.write('\n')
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def skipped_stage(reason):
    return {'status': 'skipped', 'reason': reason}


def initial_result(trigger_sha, branch, run_id):
    return {
        'schema_version': 2,
        'trigger_sha': trigger_sha,
        'branch': branch,
        'run_id': str(run_id),
        'timestamp': utc_now(),
        'status': 'failed',
        'stages': {stage: skipped_stage('stage has not completed') for stage in STAGES},
    }


def initialize(output_dir, trigger_sha, branch, run_id):
    validate_identity(trigger_sha, run_id)
    output_dir.mkdir(parents=True, exist_ok=True)
    marker = (f'Build and Test has not run for trigger {trigger_sha} '
              f'(run {run_id}, branch {branch}).\n')
    (output_dir / 'build.log').write_text(marker, encoding='utf-8')
    write_json(output_dir / 'latest.json', initial_result(trigger_sha, branch, run_id))


def stage_status(outcome):
    mapping = {'success': 'passed', 'failure': 'failed', 'cancelled': 'failed', 'skipped': 'skipped'}
    if outcome not in mapping:
        raise ValueError('unsupported GitHub step outcome: ' + repr(outcome))
    return mapping[outcome]


def optional_exit_code(value):
    if value in (None, ''):
        return None
    return int(value)


def read_object(path):
    if not path.is_file():
        return None
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict):
        raise ValueError(str(path) + ' must contain a JSON object')
    return value


def stage(outcome, exit_code=None):
    result = {'status': stage_status(outcome)}
    code = optional_exit_code(exit_code)
    if code is not None:
        result['exit_code'] = code
        if code != 0 and result['status'] == 'passed':
            result['status'] = 'failed'
    return result


def candidate_stage(outcome, exit_code, bundle):
    result = stage(outcome, exit_code)
    if bundle is None or not bundle.is_dir():
        if result['status'] != 'skipped':
            result['status'] = 'failed'
            result['reasons'] = ['Candidate bundle is missing']
        return result

    candidate = read_object(bundle / 'candidate.json')
    evaluation = read_object(bundle / 'evaluation.json')
    report = read_object(bundle / 'report.json')
    if candidate:
        result['candidate_digest'] = candidate.get('digest')
    if evaluation:
        result['evaluation_digest'] = evaluation.get('digest')
    if report:
        result['report_digest'] = report.get('digest')
        reasons = report.get('reasons')
        if isinstance(reasons, list):
            result['reasons'] = reasons
        if report.get('status') != 'passed':
            result['status'] = 'failed'
    elif result['status'] != 'skipped':
        result['status'] = 'failed'
        result['reasons'] = ['Candidate report is missing']
    return result


def finalize(args):
    validate_identity(args.trigger_sha, args.run_id)
    workflow = stage(args.workflow_tests_outcome)
    candidate = candidate_stage(args.candidate_outcome, args.candidate_exit_code, args.candidate_bundle)
    build = stage(args.build_outcome, args.build_exit_code)
    if args.test_summary_file and args.test_summary_file.is_file():
        build['test_summary'] = args.test_summary_file.read_text(encoding='utf-8', errors='replace').strip()
    stages = {
        'candidate_workflow_tests': workflow,
        'frozen_native_candidate': candidate,
        'build_and_test': build,
    }
    result = {
        'schema_version': 2,
        'trigger_sha': args.trigger_sha,
        'branch': args.branch,
        'run_id': str(args.run_id),
        'timestamp': utc_now(),
        'status': 'passed' if all(value['status'] == 'passed' for value in stages.values()) else 'failed',
        'artifact_name': args.artifact_name,
        'stages': stages,
    }
    write_json(args.output_dir / 'latest.json', result)
    print(json.dumps(result, ensure_ascii=False))


def verify(output_dir, trigger_sha, run_id):
    validate_identity(trigger_sha, run_id)
    result = read_object(output_dir / 'latest.json')
    if result is None:
        raise ValueError('latest.json is missing')
    if result.get('trigger_sha') != trigger_sha or str(result.get('run_id')) != str(run_id):
        raise ValueError('CI result identity does not match this run')
    stages = result.get('stages')
    if not isinstance(stages, dict) or set(stages) != set(STAGES):
        raise ValueError('CI result does not contain the required stage ledger')
    statuses = {name: value.get('status') for name, value in stages.items() if isinstance(value, dict)}
    print(json.dumps({'status': result.get('status'), 'trigger_sha': trigger_sha,
                      'run_id': str(run_id), 'stages': statuses}, ensure_ascii=False))
    return result.get('status') == 'passed' and all(statuses.get(name) == 'passed' for name in STAGES)


def parser():
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest='action', required=True)
    init = commands.add_parser('init')
    final = commands.add_parser('finalize')
    check = commands.add_parser('verify')
    for command in (init, final, check):
        command.add_argument('--output-dir', type=Path, required=True)
        command.add_argument('--trigger-sha', required=True)
        command.add_argument('--run-id', required=True)
    for command in (init, final):
        command.add_argument('--branch', required=True)
    final.add_argument('--artifact-name', required=True)
    final.add_argument('--workflow-tests-outcome', required=True)
    final.add_argument('--candidate-outcome', required=True)
    final.add_argument('--candidate-exit-code')
    final.add_argument('--candidate-bundle', type=Path)
    final.add_argument('--build-outcome', required=True)
    final.add_argument('--build-exit-code')
    final.add_argument('--test-summary-file', type=Path)
    return root


def main():
    args = parser().parse_args()
    try:
        if args.action == 'init':
            initialize(args.output_dir, args.trigger_sha, args.branch, args.run_id)
        elif args.action == 'finalize':
            finalize(args)
        else:
            return 0 if verify(args.output_dir, args.trigger_sha, args.run_id) else 1
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as error:
        print('Error: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
