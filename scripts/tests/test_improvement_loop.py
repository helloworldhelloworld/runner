"""CLI/file-system acceptance first; focused report validation below."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

CLI = Path(__file__).resolve().parents[1] / 'improvement_loop.py'


def load_workflow():
    spec = importlib.util.spec_from_file_location('improvement_loop', CLI)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class CandidateCliTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.put('pom.xml', '<project/>')
        self.put('agent-kernel/pom.xml', '<project/>')
        self.put('agent-kernel/src/main/java/Loop.java', 'class Loop { int version = 1; }')
        self.put('agent-kernel/src/test/java/LoopTest.java', 'class LoopTest {}')
        self.put('.gitignore', 'target/\n.env\n__pycache__/\n')
        self.spec = {'schemaVersion': 1, 'id': 'lifecycle', 'module': 'agent-kernel',
                     'tests': ['example.LoopTest'], 'timeoutSeconds': 60,
                     'requiredEvidence': [{'file': 'case.json', 'caseId': 'lifecycle'}],
                     'feedback': [{'source': 'developer', 'observation': 'old child ran',
                                   'expected': 'old child cancelled'}],
                     'changeScope': ['runtime', 'tool policy']}
        self.put('evals/case.json', json.dumps(self.spec))
        if CLI.exists():
            self.put('scripts/improvement_loop.py', CLI.read_text())
        subprocess.run(['git', 'init', '-q', str(self.repo)], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.repo), '-c', 'user.name=Test', '-c',
                        'user.email=test@example.invalid', 'commit', '-qm', 'baseline'], check=True)
        self.bundle = self.root / 'candidate'

    def put(self, name, value):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)
        return path

    def cli(self, *args, ok=True):
        result = subprocess.run([sys.executable, '-B', str(CLI), *map(str, args)],
                                text=True, capture_output=True)
        if ok:
            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        else:
            self.assertNotEqual(0, result.returncode, result.stdout)
        return result

    def capture(self, output=None):
        output = output or self.bundle
        self.cli('capture', '--repo', self.repo, '--spec', 'evals/case.json', '--output', output)
        return json.loads((output / 'candidate.json').read_text())

    def test_capture_preserves_dirty_runtime_untracked_tool_and_separate_eval_version(self):
        self.put('agent-kernel/src/main/java/Loop.java', 'class Loop { int version = 2; }')
        self.put('agent-kernel/src/main/java/NewTool.java', 'class NewTool {}')
        self.put('target/stale.xml', 'stale success')
        self.put('.env', 'DO_NOT_COPY')
        self.put('ci-results/latest.json', 'generated')
        self.put('.worktrees/issue-200/local.txt', 'separate worktree')
        candidate = self.capture()
        snapshot = self.bundle / 'snapshot'
        self.assertIn('version = 2', (snapshot / 'agent-kernel/src/main/java/Loop.java').read_text())
        self.assertTrue((snapshot / 'agent-kernel/src/main/java/NewTool.java').exists())
        for ignored in ['target', '.env', '.git', 'ci-results', '.worktrees']:
            self.assertFalse((snapshot / ignored).exists(), ignored)
        self.assertEqual(64, len(candidate['digest']))
        evaluation = json.loads((self.bundle / 'evaluation.json').read_text())
        self.assertEqual(self.spec, evaluation['spec'])
        second = self.capture(self.root / 'second')
        self.assertEqual(candidate['digest'], second['digest'])
        self.put('agent-kernel/src/test/java/LoopTest.java', 'class LoopTest { boolean changed; }')
        third = self.root / 'third'
        self.capture(third)
        self.assertNotEqual(evaluation['digest'], json.loads((third / 'evaluation.json').read_text())['digest'])

    def test_changed_source_rejects_old_candidate_even_when_head_is_unchanged(self):
        self.capture()
        self.cli('verify', '--bundle', self.bundle, '--repo', self.repo)
        self.put('agent-kernel/src/main/java/NewTool.java', 'class NewTool {}')
        result = self.cli('verify', '--bundle', self.bundle, '--repo', self.repo, ok=False)
        self.assertIn('workspace', result.stderr.lower())

    def test_snapshot_tampering_is_rejected_before_maven_starts(self):
        self.capture()
        (self.bundle / 'snapshot/agent-kernel/src/main/java/Loop.java').write_text('changed')
        result = self.cli('evaluate', '--bundle', self.bundle, ok=False)
        self.assertIn('snapshot', result.stderr.lower())
        self.assertFalse((self.bundle / 'build.log').exists())

    def test_deleted_or_added_snapshot_input_is_detected(self):
        self.capture()
        (self.bundle / 'snapshot/extra.java').write_text('new input')
        self.cli('verify', '--bundle', self.bundle, ok=False)

    def test_changed_eval_definition_is_rejected(self):
        self.capture()
        evaluation = json.loads((self.bundle / 'evaluation.json').read_text())
        evaluation['spec']['tests'] = ['example.WeakerTest']
        (self.bundle / 'evaluation.json').write_text(json.dumps(evaluation))
        self.cli('verify', '--bundle', self.bundle, ok=False)

    def test_output_must_not_be_inside_repo_or_overwrite_existing_bundle(self):
        self.cli('capture', '--repo', self.repo, '--spec', 'evals/case.json',
                 '--output', self.repo / 'bundle', ok=False)
        self.capture()
        marker = self.bundle / 'keep'
        marker.write_text('preserve')
        self.cli('capture', '--repo', self.repo, '--spec', 'evals/case.json',
                 '--output', self.bundle, ok=False)
        self.assertEqual('preserve', marker.read_text())

    def test_symlink_cannot_import_files_outside_the_candidate(self):
        outside = self.root / 'external.txt'
        outside.write_text('private')
        (self.repo / 'external.txt').symlink_to(outside)
        self.cli('capture', '--repo', self.repo, '--spec', 'evals/case.json',
                 '--output', self.bundle, ok=False)
        self.assertFalse(self.bundle.exists())

    def test_traversal_in_manifest_is_rejected(self):
        self.capture()
        manifest = json.loads((self.bundle / 'candidate.json').read_text())
        manifest['files'][0]['path'] = '../external.txt'
        (self.bundle / 'candidate.json').write_text(json.dumps(manifest))
        self.cli('verify', '--bundle', self.bundle, ok=False)

    def test_saved_report_and_downstream_payload_cannot_change_without_invalidation(self):
        # A small saved-report fixture exercises the verifier, not the Maven executor.
        workflow = load_workflow()
        candidate = self.capture()
        evaluation = json.loads((self.bundle / 'evaluation.json').read_text())
        (self.bundle / 'evidence').mkdir()
        evidence = self.bundle / 'evidence/case.json'
        workflow.write_json(evidence, {'schemaVersion': 1, 'caseId': 'lifecycle',
                            'candidateDigest': candidate['digest'], 'evaluationDigest': evaluation['digest'],
                            'observations': {'actuatorRequests': [{'request': 'new B'}]}})
        (self.bundle / 'build.log').write_text('saved test output')
        report = {'schema': 'runner.report.v1', 'candidateDigest': candidate['digest'],
                  'evaluationDigest': evaluation['digest'], 'exitCode': 0, 'status': 'passed', 'reasons': [],
                  'cases': [{'class': 'example.LoopTest', 'name': 'roundTrip', 'status': 'passed', 'detail': ''}]}
        workflow.write_handoff(self.bundle, report, self.spec)
        report['outputs'] = workflow.output_records(self.bundle)
        report['digest'] = workflow.digest(report)
        report_path = self.bundle / 'report.json'
        workflow.write_json(report_path, report)
        self.cli('verify', '--bundle', self.bundle, '--repo', self.repo)
        original_report = report_path.read_text()
        report['cases'][0]['name'] = 'different case'
        workflow.write_json(report_path, report)
        self.assertIn('Report digest', self.cli('verify', '--bundle', self.bundle, ok=False).stderr)
        report_path.write_text(original_report)
        original_evidence = evidence.read_text()
        evidence.write_text(original_evidence.replace('new B', 'old A'))
        self.assertIn('outputs changed', self.cli('verify', '--bundle', self.bundle, ok=False).stderr)
        evidence.write_text(original_evidence)
        self.put('agent-kernel/src/main/java/Loop.java', 'class Loop { int version = 3; }')
        self.assertIn('workspace', self.cli('verify', '--bundle', self.bundle, '--repo', self.repo, ok=False).stderr)

    def test_missing_output_invalidates_saved_hash_records(self):
        workflow = load_workflow()
        candidate = self.capture()
        evaluation = json.loads((self.bundle / 'evaluation.json').read_text())
        (self.bundle / 'evidence').mkdir()
        log = self.bundle / 'build.log'
        log.write_text('compiler failed')
        report = {'schema': 'runner.report.v1', 'candidateDigest': candidate['digest'],
                  'evaluationDigest': evaluation['digest'], 'exitCode': 1, 'status': 'failed',
                  'cases': [], 'reasons': ['compiler failed']}
        workflow.write_handoff(self.bundle, report, self.spec)
        report['outputs'] = workflow.output_records(self.bundle)
        report['digest'] = workflow.digest(report)
        workflow.write_json(self.bundle / 'report.json', report)
        self.assertEqual('failed', workflow.verify(self.bundle)['status'])
        log.unlink()
        with self.assertRaisesRegex(ValueError, 'outputs changed'):
            workflow.verify(self.bundle)

    def test_real_maven_build_failure_produces_failed_report_and_handoff(self):
        # The fixture POM intentionally lacks required Maven model fields.
        # Let real Maven reject it; do not fake a successful executor or JUnit result.
        self.capture()
        self.cli('evaluate', '--bundle', self.bundle, ok=False)
        report = json.loads((self.bundle / 'report.json').read_text())
        self.assertEqual('failed', report['status'])
        self.assertNotEqual(0, report['exitCode'])
        self.assertEqual([], report['cases'])
        self.assertTrue(any('Missing downstream evidence' in reason for reason in report['reasons']))
        self.assertTrue(any('Required test did not execute' in reason for reason in report['reasons']))
        self.assertIn('Result: failed', (self.bundle / 'handoff.md').read_text())
        self.assertIn('Maven', report['environment']['mavenVersion'])
        self.assertEqual('failed', json.loads(self.cli('verify', '--bundle', self.bundle, ok=False).stdout)['status'])
        replay = self.root / 'replay'
        self.put('agent-kernel/src/main/java/Loop.java', 'class Loop { int changed = 99; }')
        self.cli('replay', '--bundle', self.bundle, '--output', replay, ok=False)
        self.assertTrue((replay / 'report.json').exists(), 'Replay did not execute the captured candidate')
        replay_report = json.loads((replay / 'report.json').read_text())
        self.assertEqual(report['candidateDigest'], replay_report['candidateDigest'])
        self.assertEqual(report['evaluationDigest'], replay_report['evaluationDigest'])
        self.assertEqual('failed', replay_report['status'])
        self.assertNotEqual(report['command'], replay_report['command'])
        self.assertNotIn('changed = 99', (replay / 'snapshot/agent-kernel/src/main/java/Loop.java').read_text())
        self.assertEqual(report, json.loads((self.bundle / 'report.json').read_text()))


class ReportValidationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = load_workflow()

    def test_zero_skipped_failed_or_wrong_class_cannot_pass(self):
        spec = {'tests': ['example.RequiredTest']}
        good = {'class': 'example.RequiredTest', 'name': 'roundTrip', 'status': 'passed'}
        self.assertEqual([], self.workflow.assess(spec, [good], 0, []))
        for cases, code, errors in [([], 0, []), ([dict(good, status='skipped')], 0, []),
                                    ([dict(good, status='failure')], 0, []), ([good], 1, []),
                                    ([dict(good, **{'class': 'other.Test'})], 0, []),
                                    ([good], 0, ['Missing downstream evidence'])]:
            with self.subTest(cases=cases, code=code):
                self.assertTrue(self.workflow.assess(spec, cases, code, errors))

    def test_junit_parser_reads_actual_case_status_even_with_misleading_suite_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            snapshot = Path(directory)
            reports = snapshot / 'agent-kernel/target/surefire-reports'
            reports.mkdir(parents=True)
            (reports / 'TEST-example.xml').write_text('''<testsuite name="example.RequiredTest" tests="99" failures="0">
              <properties><property name="java.version" value="21"/></properties>
              <testcase classname="example.RequiredTest" name="roundTrip"><failure message="wrong payload">expected new B</failure></testcase>
              <testcase classname="example.RequiredTest" name="notRun"><skipped/></testcase>
            </testsuite>''')
            cases, props = self.workflow.junit_results(snapshot)
            self.assertEqual(['failure', 'skipped'], [c['status'] for c in cases])
            self.assertIn('expected new B', cases[0]['detail'])
            self.assertEqual('21', props[0]['java.version'])

    def test_evidence_requires_actual_observations_and_both_content_bindings(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            (bundle / 'evidence').mkdir()
            candidate = {'digest': 'a' * 64}
            evaluation = {'digest': 'b' * 64, 'spec': {'requiredEvidence': [{'file': 'case.json', 'caseId': 'native'}]}}
            self.assertTrue(self.workflow.evidence_errors(bundle, candidate, evaluation))
            data = {'schemaVersion': 1, 'caseId': 'native', 'candidateDigest': candidate['digest'],
                    'evaluationDigest': evaluation['digest'], 'observations': {'actualToolArgs': {'request': 'B'}}}
            path = bundle / 'evidence/case.json'
            self.workflow.write_json(path, data)
            self.assertEqual([], self.workflow.evidence_errors(bundle, candidate, evaluation))
            for changed in [dict(data, candidateDigest='c' * 64), dict(data, evaluationDigest='d' * 64),
                            dict(data, caseId='wrong'), dict(data, observations={})]:
                self.workflow.write_json(path, changed)
                self.assertTrue(self.workflow.evidence_errors(bundle, candidate, evaluation))

    def test_definition_rejects_unimplemented_options_or_implicit_test_selection(self):
        fixture = {'schemaVersion': 1, 'id': 'native', 'module': 'agent-kernel', 'tests': ['example.Test'],
                   'timeoutSeconds': 60, 'requiredEvidence': [{'file': 'case.json', 'caseId': 'native'}],
                   'feedback': [{'source': 'developer', 'observation': 'A', 'expected': 'B'}], 'changeScope': ['runtime']}
        self.workflow.validate_spec(fixture)
        for changed in [dict(fixture, tests=[]), dict(fixture, tests=['*Test']), dict(fixture, command=['true']),
                        dict(fixture, timeoutSeconds=0), dict(fixture, requiredEvidence=[]),
                        dict(fixture, requiredEvidence=[{'file': '../case.json', 'caseId': 'native'}])]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                self.workflow.validate_spec(changed)


if __name__ == '__main__':
    unittest.main()
