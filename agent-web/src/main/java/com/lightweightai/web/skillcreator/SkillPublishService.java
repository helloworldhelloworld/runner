package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ConfigurationContent;
import com.lightweightai.kernel.prompt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Content-bound publication. Durable active versions are the native prompt source. */
public class SkillPublishService implements PromptSource {
    private static final Logger logger = LoggerFactory.getLogger(SkillPublishService.class);
    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final TestRunRepository testRunRepository;
    private final SkillTestCaseRepository testCaseRepository;
    private final PromptEngine promptEngine;
    private final ToolRegistry toolRegistry;
    private final com.lightweightai.kernel.instruction.InstructionRegistry instructionRegistry;

    public SkillPublishService(SkillRepository skills, SkillVersionRepository versions, TestRunRepository runs,
                               PromptEngine prompts, ToolRegistry tools,
                               com.lightweightai.kernel.instruction.InstructionRegistry instructions) {
        this(skills, versions, runs, prompts, tools, instructions, new SkillTestCaseRepository(skills.databasePath()));
    }

    public SkillPublishService(SkillRepository skills, SkillVersionRepository versions, TestRunRepository runs,
                               PromptEngine prompts, ToolRegistry tools,
                               com.lightweightai.kernel.instruction.InstructionRegistry instructions,
                               SkillTestCaseRepository cases) {
        skillRepository = skills;
        versionRepository = versions;
        testRunRepository = runs;
        testCaseRepository = cases;
        promptEngine = prompts;
        toolRegistry = tools;
        instructionRegistry = instructions;
    }

    public synchronized SkillVersion stage(String skillId, double requiredPassRate) {
        SkillDraft draft = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
        List<TestRun> runs = testRunRepository.findRunsBySkillId(skillId);
        if (runs.isEmpty()) throw new IllegalStateException("No test runs found for skill: " + skillId);
        TestRun report = runs.get(0);
        SkillPublicationContent.validate(report, testRunRepository.findCaseResultsByRunId(report.getId()), draft,
                testCaseRepository.findBySkillId(skillId), requiredPassRate);
        SkillVersion version = new SkillVersion();
        version.setSkillId(skillId);
        version.setVersionTag(nextVersionTag(skillId));
        version.setSnapshotJson(report.getCandidateJson());
        version.setCandidateDigest(report.getCandidateDigest());
        version.setEvaluationDigest(report.getEvaluationDigest());
        version.setReportDigest(report.getReportDigest());
        version.setRequiredPassRate(requiredPassRate);
        version.setTestRunId(report.getId());
        version.setStatus("staged");
        return versionRepository.save(version);
    }

    public SkillVersion activate(String versionId) { return activate(versionId, null, false, null); }

    /** Conditional publication; null means that no active version is expected. */
    public SkillVersion activate(String versionId, String expectedActiveVersionId) {
        return activate(versionId, expectedActiveVersionId, true, null);
    }

    public SkillVersion rollback(String skillId, String targetVersionId) {
        return activate(targetVersionId, null, false, Objects.requireNonNull(skillId));
    }

    private SkillVersion activate(String versionId, String expectedActive, boolean conditional, String expectedSkill) {
        SkillVersion activated = versionRepository.activateAtomically(versionId, expectedActive, conditional, version -> {
            if (expectedSkill != null && !expectedSkill.equals(version.getSkillId()))
                throw new IllegalArgumentException("Rollback target belongs to another skill");
            if (!Set.of("staged", "active", "archived").contains(version.getStatus()))
                throw new IllegalStateException("Version cannot be activated from status: " + version.getStatus());
            SkillDraft draft = validatedCandidate(version, true);
            buildSkill(version, draft, true);
        });
        // Compatibility views follow the committed source; native execution reads the durable version directly.
        SkillDraft draft = SkillPublicationContent.draft(activated.getSnapshotJson());
        try {
            promptEngine.registerSkill(buildSkill(activated, draft, false));
            skillRepository.updateStatus(activated.getSkillId(), "active");
            if (instructionRegistry != null) {
                if (instructionRegistry.getPackage(draft.getName()) != null) instructionRegistry.deactivatePackage(draft.getName());
                instructionRegistry.registerPackage(new SimpleInstructionPackage(draft.getName(), draft.getDescription(), draft.getSystemPrompt()));
                instructionRegistry.activatePackage(draft.getName());
            }
        } catch (Exception e) {
            logger.warn("Publication committed; compatibility view refresh failed for {}", activated.getId(), e);
        }
        return activated;
    }

    public List<SkillVersion> getVersionHistory(String skillId) { return versionRepository.findBySkillId(skillId); }

    @Override
    public PromptSnapshot snapshot() {
        List<PromptSnapshot.Fragment> content = new ArrayList<>(promptEngine.snapshot().getFragments().stream()
                .filter(f -> !f.metadata().containsKey("publishedSkillId")).toList());
        Set<String> names = new HashSet<>(content.stream().map(PromptSnapshot.Fragment::name).toList());
        for (SkillVersion version : versionRepository.findActiveVersions()) {
            SkillDraft draft = validatedCandidate(version, false);
            if (!names.add(draft.getName())) throw new IllegalStateException("Published prompt name conflicts: " + draft.getName());
            content.add(fragment(version, draft));
        }
        return new PromptSnapshot(content);
    }

    private SkillDraft validatedCandidate(SkillVersion version, boolean publication) {
        SkillDraft draft = SkillPublicationContent.draft(version.getSnapshotJson());
        if (!version.getSkillId().equals(draft.getId()) || version.getCandidateDigest() == null
                || !version.getCandidateDigest().equals(SkillPublicationContent.candidateDigest(draft)))
            throw new IllegalStateException("Version content is unbound or changed");
        TestRun report = testRunRepository.findRunById(version.getTestRunId())
                .orElseThrow(() -> new IllegalStateException("Bound test report not found"));
        if (!version.getSkillId().equals(report.getSkillId())
                || !Objects.equals(version.getCandidateDigest(), report.getCandidateDigest())
                || !Objects.equals(version.getEvaluationDigest(), report.getEvaluationDigest())
                || !Objects.equals(version.getReportDigest(), report.getReportDigest()))
            throw new IllegalStateException("Version does not match its test report");
        var results = testRunRepository.findCaseResultsByRunId(report.getId());
        if (publication) SkillPublicationContent.validate(report, results, draft,
                testCaseRepository.findBySkillId(version.getSkillId()), version.getRequiredPassRate());
        else SkillPublicationContent.validateStored(report, results, draft, version.getRequiredPassRate());
        return draft;
    }

    private Skill buildSkill(SkillVersion version, SkillDraft draft, boolean validateTools) {
        if (!draft.isValid()) throw new IllegalStateException("Published skill requires a name, description and prompt");
        promptEngine.getSkill(draft.getName()).ifPresent(existing -> {
            Object owner = existing.getMetadata().get("publishedSkillId");
            if (!version.getSkillId().equals(owner)) throw new IllegalStateException("Prompt name is already registered: " + draft.getName());
        });
        Skill.Builder builder = Skill.builder().name(draft.getName()).description(draft.getDescription())
                .systemPrompt(draft.getSystemPrompt()).triggers(draft.getTriggers()).priority(draft.getPriority());
        for (String toolName : draft.getToolNames()) {
            Optional<Tool> tool = toolRegistry.get(toolName);
            if (validateTools && tool.isEmpty()) throw new IllegalStateException("Published tool is unavailable: " + toolName);
            if (validateTools) ConfigurationContent.map(tool.orElseThrow().getSchema().toMap());
            tool.ifPresent(builder::addTool);
        }
        metadata(version, draft).forEach(builder::metadata);
        return builder.build();
    }
    private PromptSnapshot.Fragment fragment(SkillVersion version, SkillDraft draft) {
        return new PromptSnapshot.Fragment(draft.getName(), draft.getSystemPrompt(), draft.getTriggers(), draft.getPriority(), metadata(version, draft));
    }
    private Map<String, Object> metadata(SkillVersion version, SkillDraft draft) {
        Map<String, Object> result = new LinkedHashMap<>(draft.getMetadata());
        result.put("publishedSkillId", version.getSkillId());
        result.put("publishedVersionId", version.getId());
        result.put("candidateDigest", version.getCandidateDigest());
        result.put("evaluationDigest", version.getEvaluationDigest());
        return ConfigurationContent.map(result);
    }
    private String nextVersionTag(String skillId) {
        List<SkillVersion> versions = versionRepository.findBySkillId(skillId);
        if (versions.isEmpty()) return "1.0.0";
        String[] parts = versions.get(0).getVersionTag().split("\\.");
        return parts.length == 3 ? parts[0] + "." + parts[1] + "." + (Integer.parseInt(parts[2]) + 1) : "1.0.0";
    }
}
