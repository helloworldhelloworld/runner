package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.ConfigurationContent;
import com.lightweightai.kernel.core.DefaultTestExecutor;

import java.security.MessageDigest;
import java.util.*;

/** Candidate, evaluation and result content are separate identities. */
final class SkillPublicationContent {
    private static final ObjectMapper JSON = new ObjectMapper();
    private SkillPublicationContent() {}

    static Map<String, Object> candidate(SkillDraft draft) {
        Map<String, Object> value = new LinkedHashMap<>(draft.toMap());
        value.remove("status");
        return ConfigurationContent.map(value);
    }
    static String candidateDigest(SkillDraft draft) { return ConfigurationContent.digest(candidate(draft)); }
    static SkillDraft draft(String json) {
        try { return JSON.readValue(json, SkillDraft.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid candidate snapshot", e); }
    }
    static String json(Object value) {
        try { return JSON.writeValueAsString(ConfigurationContent.freeze(value)); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot serialize publication content", e); }
    }
    static Map<String, Object> evaluation(List<SkillTestCase> cases) {
        List<Object> content = cases.stream().sorted(Comparator.comparing(SkillTestCase::getId))
                .map(c -> (Object) JSON.convertValue(c, Map.class)).toList();
        Map<String, String> validator = new TreeMap<>();
        for (Class<?> type : List.of(SkillPublicationContent.class, SkillTestExecutionService.class,
                SkillTesterService.class, DefaultTestExecutor.class)) {
            try (var bytes = type.getResourceAsStream(type.getSimpleName() + ".class")) {
                if (bytes == null) throw new IllegalStateException("Missing validator class: " + type.getName());
                validator.put(type.getName(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())));
            } catch (Exception e) { throw new IllegalStateException("Cannot identify validator", e); }
        }
        return ConfigurationContent.map(Map.of("format", "runner-skill-evaluation-v1", "validator", validator, "cases", content));
    }
    static String evaluationDigest(List<SkillTestCase> cases) { return ConfigurationContent.digest(evaluation(cases)); }
    static String reportDigest(TestRun run, List<TestCaseResult> results) {
        List<Object> cases = results.stream().sorted(Comparator.comparing(TestCaseResult::getTestCaseId))
                .map(result -> (Object) JSON.convertValue(result, Map.class)).toList();
        return ConfigurationContent.digest(Map.of("candidateDigest", run.getCandidateDigest(),
                "evaluationDigest", run.getEvaluationDigest(), "total", run.getTotal(), "passed", run.getPassed(),
                "failed", run.getFailed(), "status", run.getStatus(), "results", cases));
    }
    static void validate(TestRun run, List<TestCaseResult> results, SkillDraft draft, List<SkillTestCase> cases, double threshold) {
        validateStored(run, results, draft, threshold);
        if (run.getTotal() != cases.size() || !evaluationDigest(cases).equals(run.getEvaluationDigest()))
            throw new IllegalStateException("Evaluation changed; run tests again");
    }
    static void validateStored(TestRun run, List<TestCaseResult> results, SkillDraft draft, double threshold) {
        List<SkillTestCase> cases = frozenCases(run);
        if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1)
            throw new IllegalArgumentException("Pass-rate threshold must be finite and in (0, 1]");
        if (!"completed".equals(run.getStatus()) || run.getTotal() <= 0 || run.getTotal() != results.size()
                || run.getTotal() != cases.size() || run.getPassed() < 0 || run.getFailed() < 0
                || run.getPassed() + run.getFailed() != run.getTotal())
            throw new IllegalStateException("Test report is incomplete");
        Set<String> expected = new HashSet<>(cases.stream().map(SkillTestCase::getId).toList());
        Set<String> actual = new HashSet<>(results.stream().map(TestCaseResult::getTestCaseId).toList());
        if (actual.size() != results.size() || !actual.equals(expected)
                || results.stream().filter(TestCaseResult::isPassed).count() != run.getPassed())
            throw new IllegalStateException("Test results do not match the declared cases");
        if (run.getCandidateJson() == null || run.getEvaluationJson() == null || run.getReportDigest() == null
                || !candidateDigest(draft).equals(run.getCandidateDigest())
                || !candidateDigest(draft(run.getCandidateJson())).equals(run.getCandidateDigest()))
            throw new IllegalStateException("Candidate changed or the test report has no content binding; run tests again");
        if (!ConfigurationContent.digest(read(run.getEvaluationJson())).equals(run.getEvaluationDigest()))
            throw new IllegalStateException("Evaluation changed; run tests again");
        if (!reportDigest(run, results).equals(run.getReportDigest()))
            throw new IllegalStateException("Test report or results changed");
        if (run.getPassRate() < threshold) throw new IllegalStateException("Pass rate is below the required threshold");
    }
    @SuppressWarnings("unchecked")
    private static List<SkillTestCase> frozenCases(TestRun run) {
        if (run.getEvaluationJson() == null) throw new IllegalStateException("Legacy report has no evaluation binding; run tests again");
        Object value = read(run.getEvaluationJson());
        if (!(value instanceof Map<?, ?> map) || !(map.get("cases") instanceof List<?> cases))
            throw new IllegalStateException("Invalid evaluation snapshot");
        return cases.stream().map(c -> JSON.convertValue(c, SkillTestCase.class)).toList();
    }
    private static Object read(String text) {
        try { return JSON.readValue(text, Object.class); }
        catch (Exception e) { throw new IllegalStateException("Invalid evaluation snapshot", e); }
    }
}
