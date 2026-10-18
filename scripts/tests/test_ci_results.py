"""Outside-in checks for trustworthy CI stage results and workflow wiring."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'scripts/ci_results.py'
WORKFLOW = ROOT / '.github/workflows/ci.yml'


class CiResultCliTest(unittest.TestCase):
    SHA = '388147f7da58d58bbd5608ca3cc5da69a4da1dfa'

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.results = self.root / 'ci-results'
        self.bundle = self.root / 'candidate'

    def cli(self, *args):
        result = subprocess.run([sys.executable, '-B', str(CLI), *map(str, args)],
                                text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        return result

    def init(self):
        return self.cli('init', '--output-dir', self.results, '--trigger-sha', self.SHA,
                        '--branch', 'main', '--run-id', '39095202090')

    def test_init_replaces_stale_build_log_with_current_run_identity(self):
        self.results.mkdir()
        (self.results / 'build.log').write_text('BUILD SUCCESS\nFinished at: 2026-08-02')

        self.init()

        log = (self.results / 'build.log').read_text()
        self.assertNotIn('2026-08-02', log)
        self.assertIn(self.SHA, log)
        self.assertIn('39095202090', log)

    def test_finalize_binds_stage_ledger_and_failed_candidate_evidence_to_trigger_sha(self):
        self.init()
        self.bundle.mkdir()
        (self.bundle / 'candidate.json').write_text(json.dumps({'digest': 'a' * 64}))
        (self.bundle / 'evaluation.json').write_text(json.dumps({'digest': 'b' * 64}))
        (self.bundle / 'report.json').write_text(json.dumps({
            'digest': 'c' * 64, 'status': 'failed', 'exitCode': 1,
            'reasons': ['Required test failed'], 'cases': []
        }))
        summary = self.root / 'test-summary.txt'
        summary.write_text('Tests run: 2380, Failures: 0, Errors: 0, Skipped: 22\n')

        self.cli('finalize', '--output-dir', self.results, '--trigger-sha', self.SHA,
                 '--branch', 'main', '--run-id', '39095202090',
                 '--artifact-name', 'runner-ci-39095202090-388147f7',
                 '--workflow-tests-outcome', 'success',
                 '--candidate-outcome', 'failure', '--candidate-exit-code', '1',
                 '--candidate-bundle', self.bundle,
                 '--build-outcome', 'success', '--build-exit-code', '0',
                 '--test-summary-file', summary)

        result = json.loads((self.results / 'latest.json').read_text())
        self.assertEqual(2, result['schema_version'])
        self.assertEqual(self.SHA, result['trigger_sha'])
        self.assertEqual('failed', result['status'])
        self.assertEqual('passed', result['stages']['candidate_workflow_tests']['status'])
        candidate = result['stages']['frozen_native_candidate']
        self.assertEqual('failed', candidate['status'])
        self.assertEqual(1, candidate['exit_code'])
        self.assertEqual('a' * 64, candidate['candidate_digest'])
        self.assertEqual('b' * 64, candidate['evaluation_digest'])
        self.assertEqual('c' * 64, candidate['report_digest'])
        self.assertEqual(['Required test failed'], candidate['reasons'])
        self.assertEqual('passed', result['stages']['build_and_test']['status'])
        self.assertEqual(0, result['stages']['build_and_test']['exit_code'])
        self.assertIn('Tests run: 2380', result['stages']['build_and_test']['test_summary'])
        self.assertEqual('runner-ci-39095202090-388147f7', result['artifact_name'])

    def test_skipped_stages_are_explicit_and_cannot_make_the_run_pass(self):
        self.init()
        self.cli('finalize', '--output-dir', self.results, '--trigger-sha', self.SHA,
                 '--branch', 'main', '--run-id', '39095202090',
                 '--artifact-name', 'runner-ci-39095202090-388147f7',
                 '--workflow-tests-outcome', 'failure',
                 '--candidate-outcome', 'skipped',
                 '--build-outcome', 'skipped')

        result = json.loads((self.results / 'latest.json').read_text())
        self.assertEqual('failed', result['status'])
        self.assertEqual('failed', result['stages']['candidate_workflow_tests']['status'])
        self.assertEqual('skipped', result['stages']['frozen_native_candidate']['status'])
        self.assertEqual('skipped', result['stages']['build_and_test']['status'])


class CiWorkflowWiringTest(unittest.TestCase):
    def test_failure_evidence_is_uploaded_before_the_final_gate(self):
        workflow = WORKFLOW.read_text()
        init = workflow.index('name: Initialize current CI result')
        tests = workflow.index('name: Test candidate evidence workflow')
        candidate = workflow.index('name: Run frozen native candidate')
        build = workflow.index('name: Build and Test')
        finalize = workflow.index('name: Finalize CI result')
        upload = workflow.index('uses: actions/upload-artifact@v4')
        gate = workflow.index('name: Enforce required CI stages')
        self.assertLess(init, tests)
        self.assertLess(tests, candidate)
        self.assertLess(candidate, build)
        self.assertLess(build, finalize)
        self.assertLess(finalize, upload)
        self.assertLess(upload, gate)
        self.assertGreaterEqual(workflow.count('continue-on-error: true'), 3)
        self.assertIn('if: always()', workflow[finalize:gate])
        self.assertNotIn('CLAUDE_API_KEY', workflow)


if __name__ == '__main__':
    unittest.main()
