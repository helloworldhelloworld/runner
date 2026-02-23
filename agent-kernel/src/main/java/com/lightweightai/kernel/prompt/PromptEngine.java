package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Prompt 构建引擎 (OpenClaw 风格)
 *
 * 核心职责：
 * 1. 管理 Skill 注册表
 * 2. 渐进式加载 Skill（按需/自动检测）
 * 3. 组装完整上下文（System Prompt + Memory + History）
 * 4. 输出可观测的 PromptContext
 *
 * 渐进式加载策略：
 * - 显式指定：通过 activeSkills 参数
 * - 自动检测：通过 triggers 匹配用户消息
 * - 按优先级排序：priority 小的先加载
 */
public class PromptEngine {

    private final MemoryProvider memoryProvider;
    private final Map<String, Skill> skillRegistry;
    private final String baseSystemPrompt;

    private PromptEngine(Builder builder) {
        this.memoryProvider = builder.memoryProvider;
        this.skillRegistry = new LinkedHashMap<>();
        this.baseSystemPrompt = builder.baseSystemPrompt != null ? builder.baseSystemPrompt : "";
    }

    // ==================== Skill 管理 ====================

    /**
     * 注册 Skill
     */
    public void registerSkill(Skill skill) {
        skillRegistry.put(skill.getName(), skill);
    }

    /**
     * 批量注册 Skill
     */
    public void registerSkills(List<Skill> skills) {
        skills.forEach(this::registerSkill);
    }

    /**
     * 检查是否有指定 Skill
     */
    public boolean hasSkill(String name) {
        return skillRegistry.containsKey(name);
    }

    /**
     * 获取所有注册的 Skill 名称
     */
    public List<String> getRegisteredSkills() {
        return new ArrayList<>(skillRegistry.keySet());
    }

    /**
     * 获取 Skill
     */
    public Optional<Skill> getSkill(String name) {
        return Optional.ofNullable(skillRegistry.get(name));
    }

    // ==================== Prompt 构建 ====================

    /**
     * 构建 Prompt 上下文
     */
    public PromptContext build(PromptRequest request) {
        PromptContext.Builder contextBuilder = PromptContext.builder();
        contextBuilder.userMessage(request.getUserMessage());

        List<String> buildLog = new ArrayList<>();
        buildLog.add("Starting prompt build...");

        // 1. 确定要激活的 Skills
        List<Skill> activeSkills = resolveActiveSkills(request, buildLog);
        buildLog.add(String.format("Resolved %d active skills: %s",
            activeSkills.size(),
            activeSkills.stream().map(Skill::getName).collect(Collectors.joining(", "))));

        // 2. 按优先级排序
        activeSkills.sort(Comparator.comparingInt(Skill::getPriority));

        // 3. 组装 System Prompt
        String systemPrompt = buildSystemPrompt(activeSkills, buildLog);
        contextBuilder.systemPrompt(systemPrompt);

        // 4. 收集所有工具
        List<Skill.ToolDefinition> allTools = new ArrayList<>();
        for (Skill skill : activeSkills) {
            allTools.addAll(skill.getTools());
            if (!skill.getTools().isEmpty()) {
                buildLog.add(String.format("Added %d tools from skill '%s'",
                    skill.getTools().size(), skill.getName()));
            }
        }
        contextBuilder.tools(allTools);

        // 5. 记录激活的 Skill 名称
        List<String> activeSkillNames = activeSkills.stream()
            .map(Skill::getName)
            .collect(Collectors.toList());
        contextBuilder.activeSkillNames(activeSkillNames);

        // 6. 加载记忆上下文
        if (memoryProvider != null) {
            String memoryContext = loadMemoryContext(request, buildLog);
            contextBuilder.memoryContext(memoryContext);

            // 7. 加载对话历史
            List<Message> history = loadHistory(request, buildLog);
            contextBuilder.historyMessages(history);
        }

        // 8. 添加构建日志
        buildLog.add("Prompt build complete.");
        buildLog.forEach(contextBuilder::addBuildLog);

        return contextBuilder.build();
    }

    // ==================== 内部方法 ====================

    /**
     * 解析要激活的 Skills
     */
    private List<Skill> resolveActiveSkills(PromptRequest request, List<String> buildLog) {
        List<Skill> result = new ArrayList<>();

        // 1. 显式指定的 Skills
        for (String skillName : request.getActiveSkills()) {
            Skill skill = skillRegistry.get(skillName);
            if (skill != null) {
                result.add(skill);
            } else {
                buildLog.add(String.format("Warning: Skill '%s' not found", skillName));
            }
        }

        // 2. 自动检测 Skills
        if (request.isAutoDetectSkills()) {
            String userMessage = request.getUserMessage();
            for (Skill skill : skillRegistry.values()) {
                if (!result.contains(skill) && skill.matchesTrigger(userMessage)) {
                    result.add(skill);
                    buildLog.add(String.format("Auto-detected skill '%s' from triggers", skill.getName()));
                }
            }
        }

        return result;
    }

    /**
     * 构建 System Prompt
     */
    private String buildSystemPrompt(List<Skill> activeSkills, List<String> buildLog) {
        StringBuilder sb = new StringBuilder();

        // 1. 基础 System Prompt
        if (!baseSystemPrompt.isEmpty()) {
            sb.append(baseSystemPrompt).append("\n\n");
            buildLog.add("Added base system prompt");
        }

        // 2. 各 Skill 的 System Prompt
        for (Skill skill : activeSkills) {
            String skillPrompt = skill.getSystemPrompt();
            if (!skillPrompt.isEmpty()) {
                sb.append("## ").append(skill.getName()).append("\n");
                sb.append(skillPrompt).append("\n\n");
                buildLog.add(String.format("Added system prompt from skill '%s'", skill.getName()));
            }
        }

        return sb.toString().trim();
    }

    /**
     * 加载记忆上下文
     */
    private String loadMemoryContext(PromptRequest request, List<String> buildLog) {
        if (memoryProvider == null || request.getUserMessage().isEmpty()) {
            return "";
        }

        List<MemorySearchResult> results = memoryProvider.search(request.getUserMessage());

        if (results.isEmpty()) {
            buildLog.add("No relevant memory found");
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int count = Math.min(request.getMaxMemoryResults(), results.size());
        for (int i = 0; i < count; i++) {
            MemorySearchResult result = results.get(i);
            sb.append("- ").append(result.getSnippet(150)).append("\n");
        }

        buildLog.add(String.format("Loaded %d memory items", count));
        return sb.toString();
    }

    /**
     * 加载对话历史
     */
    private List<Message> loadHistory(PromptRequest request, List<String> buildLog) {
        if (memoryProvider == null || request.getSessionId() == null) {
            return Collections.emptyList();
        }

        List<Message> history = memoryProvider.getHistory(
            request.getSessionId(),
            request.getMaxHistoryMessages()
        );

        if (!history.isEmpty()) {
            buildLog.add(String.format("Loaded %d history messages", history.size()));
        }

        return history;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemoryProvider memoryProvider;
        private String baseSystemPrompt;

        public Builder memoryProvider(MemoryProvider provider) {
            this.memoryProvider = provider;
            return this;
        }

        public Builder baseSystemPrompt(String prompt) {
            this.baseSystemPrompt = prompt;
            return this;
        }

        public PromptEngine build() {
            return new PromptEngine(this);
        }
    }
}
