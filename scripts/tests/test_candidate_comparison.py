"""CLI acceptance for comparing saved report fixtures; no simulated Maven success."""
import json
import unittest
import test_improvement_loop as fixture


class CandidateComparisonCliTest(unittest.TestCase):
    setUp = fixture.CandidateCliTest.setUp
    put = fixture.CandidateCliTest.put
    cli = fixture.CandidateCliTest.cli
    capture = fixture.CandidateCliTest.capture

    def saved_report(self, bundle, cases):
        workflow = fixture.load_workflow()
        candidate = self.capture(bundle)
        evaluation = json.loads((bundle / 'evaluation.json').read_text())
        (bundle / 'evidence').mkdir()
        workflow.write_json(bundle / 'evidence/case.json', {
            'schemaVersion': 1, 'caseId': 'lifecycle', 'candidateDigest': candidate['digest'],
            'evaluationDigest': evaluation['digest'], 'observations': {'fixture': 'saved verifier input'}})
        (bundle / 'build.log').write_text('Saved-report fixture; no Maven execution')
        passed = all(status == 'passed' for status in cases.values())
        report = {'schema': 'runner.report.v1', 'candidateDigest': candidate['digest'],
                  'evaluationDigest': evaluation['digest'], 'exitCode': 0 if passed else 1,
                  'status': 'passed' if passed else 'failed', 'reasons': [] if passed else ['case failed'],
                  'cases': [{'class': 'example.LoopTest', 'name': name, 'status': status, 'detail': ''}
                            for name, status in cases.items()]}
        workflow.write_handoff(bundle, report, self.spec)
        report['outputs'] = workflow.output_records(bundle)
        report['digest'] = workflow.digest(report)
        workflow.write_json(bundle / 'report.json', report)
        return report

    def pair(self, after):
        before = self.saved_report(self.bundle, {'fixed': 'failure', 'stable': 'passed'})
        self.put('agent-kernel/src/main/java/Loop.java', 'class Loop { int version = 2; }')
        second = self.root / 'second'
        candidate = self.saved_report(second, after)
        return second, before, candidate

    def compare(self, second, output=None, ok=True):
        output = output or self.root / 'comparison'
        return self.cli('compare', '--baseline', self.bundle, '--candidate', second,
                        '--output', output, ok=ok)

    def test_regression_is_not_hidden_by_a_fixed_case(self):
        second, before, after = self.pair({'fixed': 'passed', 'stable': 'failure'})
        self.compare(second)
        report = json.loads((self.root / 'comparison/comparison.json').read_text())
        self.assertEqual('regressed', report['status'])
        self.assertEqual(before['digest'], report['baseline']['reportDigest'])
        self.assertEqual(after['digest'], report['candidate']['reportDigest'])
        changes = {item['name']: item['change'] for item in report['cases']}
        self.assertEqual({'fixed': 'fixed', 'stable': 'regressed'}, changes)
        handoff = (self.root / 'comparison/handoff.md').read_text()
        self.assertIn('stable', handoff)
        self.assertIn('old child ran', handoff)
        self.assertIn('evidence/', handoff)
        self.assertEqual(before, json.loads((self.bundle / 'report.json').read_text()))

    def test_actual_fix_is_distinguished_from_removing_a_test(self):
        second, _, _ = self.pair({'fixed': 'passed', 'stable': 'passed'})
        self.compare(second)
        result = json.loads((self.root / 'comparison/comparison.json').read_text())
        self.assertEqual('improved', result['status'])
        third = self.root / 'third'
        self.saved_report(third, {'stable': 'passed'})
        self.compare(third, self.root / 'removed-case')
        removed = json.loads((self.root / 'removed-case/comparison.json').read_text())
        self.assertEqual('incomparable', removed['status'])
        self.assertIn('removed', [case['change'] for case in removed['cases']])

    def test_changed_criteria_and_captured_only_candidates_are_rejected(self):
        self.saved_report(self.bundle, {'stable': 'passed'})
        captured = self.root / 'captured'
        self.capture(captured)
        result = self.compare(captured, ok=False)
        self.assertIn('executed', result.stderr)
        self.spec['feedback'][0]['expected'] = 'a different criterion'
        self.put('evals/case.json', json.dumps(self.spec))
        changed = self.root / 'changed'
        self.saved_report(changed, {'stable': 'passed'})
        result = self.compare(changed, ok=False)
        self.assertIn('evaluation', result.stderr.lower())
        self.assertFalse((self.root / 'comparison').exists())

    def test_modified_evidence_and_overwriting_inputs_are_rejected(self):
        second, _, _ = self.pair({'fixed': 'passed', 'stable': 'passed'})
        self.compare(second, self.bundle, ok=False)
        original = (second / 'evidence/case.json').read_text()
        (second / 'evidence/case.json').write_text(original.replace('saved verifier input', 'changed'))
        result = self.compare(second, ok=False)
        self.assertIn('outputs changed', result.stderr)
        self.assertFalse((self.root / 'comparison').exists())
