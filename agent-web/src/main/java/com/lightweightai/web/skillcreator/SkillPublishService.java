package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Skill 发布服务
 *
 * 管理版本快照、灰度发布、激活、回滚。
 */
public class SkillPublishService {

    private static final Logger logger = LoggerFactory.getLogger(SkillPublishService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final TestRunRepository testRunRepository;
    private final PromptEngine promptEngine;
    private final ToolRegistry toolRegistry;

    public SkillPublishService(SkillRepository skillRepository,
                               SkillVersionRepository versionRepository,
                               TestRunRepository testRunRepository,
                               PromptEngine promptEngine,
                               ToolRegistry toolRegistry) {
        this.skillRepository = skillRepository;
        this.versionRepository = versionRepository;
        this.testRunRepository = testRunRepository;
        this.promptEngine = promptEngine;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 创建灰度版本（staged）
     * 校验：最新 TestRun passRate >= 阈值
     */
    public SkillVersion stage(String skillId, double requiredPassRate) {
        SkillDraft draft = skillRepository.findById(skillId)
            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));

        // 校验测试通过率
        List<TestRun> runs = testRunRepository.findRunsBySkillId(skillId);
        if (runs.isEmpty()) {
            throw new IllegalStateException("No test runs found for skill: " + skillId);
        }
        TestRun latestRun = runs.get(0);
        if (!"completed".equals(latestRun.getStatus())) {
            throw new IllegalStateException("Latest test run is not completed: " + latestRun.getStatus());
        }
        if (latestRun.getPassRate() < requiredPassRate) {
            throw new IllegalStateException(String.format(
                "Pass rate %.1f%% is below threshold %.1f%%",
                latestRun.getPassRate() * 100, requiredPassRate * 100));
        }

        // 计算版本号
        String versionTag = nextVersionTag(skillId);

        // 创建不可变快照
        SkillVersion version = new SkillVersion();
        version.setSkillId(skillId);
        version.setVersionTag(versionTag);
        version.setTestRunId(latestRun.getId());
        version.setStatus("staged");
        try {
            version.setSnapshotJson(MAPPER.writeValueAsString(draft.toMap()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize skill draft", e);
        }

        versionRepository.save(version);
        logger.info("Skill {} staged as version {}", draft.getName(), versionTag);
        return version;
    }

    /**
     * 激活版本（注册到 PromptEngine，归档旧版本）
     */
    public SkillVersion activate(String versionId) {
        SkillVersion version = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        // 归档当前活跃版本
        versionRepository.findActiveBySkillId(version.getSkillId())
            .ifPresent(active -> versionRepository.updateStatus(active.getId(), "archived"));

        // 激活新版本
        versionRepository.updateStatus(versionId, "active");

        // 反序列化快照并注册到 PromptEngine
        registerVersionToPromptEngine(version);

        // 更新 SkillDraft 状态
        skillRepository.updateStatus(version.getSkillId(), "active");

        logger.info("Version {} activated for skill {}", version.getVersionTag(), version.getSkillId());
        return versionRepository.findById(versionId).orElse(version);
    }

    /**
     * 回滚到指定版本
     */
    public SkillVersion rollback(String skillId, String targetVersionId) {
        return activate(targetVersionId);
    }

    /**
     * 获取版本历史
     */
    public List<SkillVersion> getVersionHistory(String skillId) {
        return versionRepository.findBySkillId(skillId);
    }

    private String nextVersionTag(String skillId) {
        List<SkillVersion> versions = versionRepository.findBySkillId(skillId);
        if (versions.isEmpty()) return "1.0.0";

        // 解析最新版本号并递增 patch
        String latest = versions.get(0).getVersionTag();
        String[] parts = latest.split("\\.");
        if (parts.length == 3) {
            int patch = Integer.parseInt(parts[2]) + 1;
            return parts[0] + "." + parts[1] + "." + patch;
        }
        return "1.0.0";
    }

    @SuppressWarnings("unchecked")
    private void registerVersionToPromptEngine(SkillVersion version) {
        try {
            Map<String, Object> snapshot = MAPPER.readValue(version.getSnapshotJson(), Map.class);
            String name = (String) snapshot.get("name");
            String description = (String) snapshot.getOrDefault("description", "");
            String systemPrompt = (String) snapshot.getOrDefault("systemPrompt", "");
            List<String> triggers = (List<String>) snapshot.getOrDefault("triggers", List.of());
            List<String> toolNames = (List<String>) snapshot.getOrDefault("toolNames", List.of());
            int priority = snapshot.containsKey("priority") ? ((Number) snapshot.get("priority")).intValue() : 10;

            Skill.Builder builder = Skill.builder()
                .name(name)
                .description(description)
                .systemPrompt(systemPrompt)
                .triggers(triggers)
                .priority(priority);

            for (String toolName : toolNames) {
                Optional<Tool> runtimeTool = toolRegistry.get(toolName);
                runtimeTool.ifPresent(builder::addTool);
            }

            promptEngine.registerSkill(builder.build());
        } catch (Exception e) {
            logger.error("Failed to register version to PromptEngine: {}", e.getMessage(), e);
        }
    }
}
