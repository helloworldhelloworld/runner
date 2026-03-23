package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill Creator 核心服务
 *
 * 复用 AgentLoop 驱动多轮对话，引导用户创建 Skill。
 * 从 LLM 响应中解析 <skill_draft> 块，实时更新草稿。
 */
public class SkillCreatorService {

    private static final Logger logger = LoggerFactory.getLogger(SkillCreatorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DRAFT_PATTERN = Pattern.compile(
            "<skill_draft>\\s*(\\{.*?})\\s*</skill_draft>",
            Pattern.DOTALL
    );

    private final LLMProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final PromptEngine promptEngine;
    private final SkillRepository skillRepository;

    /** per-session AgentLoop + draft state */
    private final Map<String, AgentLoop> agentLoops = new ConcurrentHashMap<>();
    private final Map<String, SkillDraft> sessionDrafts = new ConcurrentHashMap<>();
    private final InMemoryMemoryProvider memoryProvider = new InMemoryMemoryProvider();

    public SkillCreatorService(LLMProvider llmProvider,
                                ToolRegistry toolRegistry,
                                PromptEngine promptEngine,
                                SkillRepository skillRepository) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.promptEngine = promptEngine;
        this.skillRepository = skillRepository;

        // 启动时加载 active skills 到 PromptEngine
        loadActiveSkills();
    }

    // ==================== Chat (streaming) ====================

    /**
     * 流式对话 - 返回 Flux<StreamEvent>，其中包含 TEXT_DELTA 和 POST_PROCESS_DATA (draft 更新)
     */
    public Flux<StreamEvent> chat(String sessionId, String message) {
        AgentLoop agentLoop = agentLoops.computeIfAbsent(sessionId, this::createAgentLoop);

        // 使用 AgentLoop 的 reactive 流式执行
        Flux<StreamEvent> flux = agentLoop.runReactive(message, sessionId);

        // 在流上累积文本，检测 <skill_draft>，发出 draft 更新事件
        StringBuilder accumulated = new StringBuilder();

        return flux.doOnNext(event -> {
            if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                accumulated.append(event.getTextDelta());
            }
        }).doOnComplete(() -> {
            // 流结束后解析 draft
            String fullText = accumulated.toString();
            parseDraft(sessionId, fullText);
        }).concatWith(Flux.defer(() -> {
            // 在流结束后追加 draft 更新事件
            SkillDraft draft = sessionDrafts.get(sessionId);
            if (draft != null) {
                try {
                    Map<String, Object> draftMap = draft.toMap();
                    return Flux.just(StreamEvent.postProcessData("skill_draft", draftMap));
                } catch (Exception e) {
                    logger.error("Failed to serialize draft", e);
                }
            }
            return Flux.empty();
        }));
    }

    /**
     * 获取当前 session 的 draft
     */
    public SkillDraft getDraft(String sessionId) {
        return sessionDrafts.getOrDefault(sessionId, new SkillDraft());
    }

    // ==================== CRUD ====================

    /**
     * 保存当前 draft 到数据库并注册到 PromptEngine
     */
    public SkillDraft save(String sessionId) {
        SkillDraft draft = sessionDrafts.get(sessionId);
        if (draft == null) {
            throw new IllegalStateException("No draft found for session: " + sessionId);
        }
        if (!draft.isValid()) {
            throw new IllegalStateException("Draft is incomplete. Need at least: name, description, systemPrompt");
        }

        draft.setStatus("active");
        SkillDraft saved = skillRepository.save(draft);

        // 注册到 PromptEngine
        registerToPromptEngine(saved);

        logger.info("Skill saved and registered: {}", saved.getName());
        return saved;
    }

    /**
     * 获取所有已保存的 Skills
     */
    public List<SkillDraft> listSkills() {
        return skillRepository.findAll();
    }

    /**
     * 删除 Skill
     */
    public boolean deleteSkill(String skillId) {
        return skillRepository.delete(skillId);
    }

    /**
     * 加载 Skill 到编辑会话
     */
    public SkillDraft loadSkill(String skillId, String sessionId) {
        Optional<SkillDraft> opt = skillRepository.findById(skillId);
        if (opt.isPresent()) {
            sessionDrafts.put(sessionId, opt.get());
            return opt.get();
        }
        throw new IllegalArgumentException("Skill not found: " + skillId);
    }

    // ==================== Internal ====================

    private AgentLoop createAgentLoop(String sessionId) {
        String systemPrompt = buildSkillCreatorSystemPrompt();

        return AgentLoop.builder()
                .llmProvider(llmProvider)
                .memoryProvider(memoryProvider)
                .toolRegistry(new ToolRegistry()) // 空工具注册表
                .systemPrompt(systemPrompt)
                .maxToolIterations(1)
                .build();
    }

    private String buildSkillCreatorSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 Skill 创建助手。通过对话帮助用户定义一个 Skill。\n\n");
        sb.append("一个 Skill 包含以下要素：\n");
        sb.append("- name: 唯一标识名（英文，snake_case，如 weather_query）\n");
        sb.append("- description: 简短的中文描述说明\n");
        sb.append("- systemPrompt: 当 Skill 激活时注入给 LLM 的系统提示词（指导 LLM 如何使用工具、如何回复用户）\n");
        sb.append("- toolNames: 关联的工具列表（从下方可用工具中选择，填写工具名称）\n");
        sb.append("- triggers: 触发词列表（用户消息包含这些词时自动激活此 Skill）\n");
        sb.append("- priority: 优先级（1-100，越小越高，默认 10）\n\n");

        // 可用工具列表
        sb.append("## 可用工具列表\n\n");
        Collection<Tool> tools = toolRegistry.getEnabled();
        if (tools.isEmpty()) {
            sb.append("（暂无可用工具）\n");
        } else {
            for (Tool tool : tools) {
                sb.append("- **").append(tool.getName()).append("**: ").append(tool.getDescription());
                if (tool instanceof ToolMetadata meta) {
                    if (meta.getCategory() != null) sb.append(" [分类: ").append(meta.getCategory()).append("]");
                }
                sb.append("\n");
            }
        }

        sb.append("\n## 对话引导规则\n\n");
        sb.append("1. 首先问用户想创建什么类型的 Skill\n");
        sb.append("2. 根据用户描述，推荐合适的工具并确认\n");
        sb.append("3. 帮用户撰写 systemPrompt（告诉 LLM 如何配合工具完成任务）\n");
        sb.append("4. 建议合理的触发词\n");
        sb.append("5. 每轮对话后，在回复末尾用 <skill_draft> 标签附上当前完整的 skill 定义 JSON\n\n");

        sb.append("## 输出格式\n\n");
        sb.append("在每次回复的末尾，如果有任何信息更新，请附上完整的 skill 定义：\n");
        sb.append("<skill_draft>\n");
        sb.append("{\n");
        sb.append("  \"name\": \"...\",\n");
        sb.append("  \"description\": \"...\",\n");
        sb.append("  \"systemPrompt\": \"...\",\n");
        sb.append("  \"toolNames\": [\"...\"],\n");
        sb.append("  \"triggers\": [\"...\"],\n");
        sb.append("  \"priority\": 10\n");
        sb.append("}\n");
        sb.append("</skill_draft>\n\n");

        sb.append("请用中文与用户对话，保持友好和专业。");

        return sb.toString();
    }

    private void parseDraft(String sessionId, String fullText) {
        Matcher matcher = DRAFT_PATTERN.matcher(fullText);
        if (matcher.find()) {
            String json = matcher.group(1);
            try {
                SkillDraft draft = MAPPER.readValue(json, SkillDraft.class);
                // 保留已有 id
                SkillDraft existing = sessionDrafts.get(sessionId);
                if (existing != null && existing.getId() != null) {
                    draft.setId(existing.getId());
                }
                sessionDrafts.put(sessionId, draft);
                logger.debug("Draft updated for session {}: name={}", sessionId, draft.getName());
            } catch (Exception e) {
                logger.warn("Failed to parse skill_draft JSON: {}", e.getMessage());
            }
        }
    }

    private void registerToPromptEngine(SkillDraft draft) {
        Skill.Builder builder = Skill.builder()
                .name(draft.getName())
                .description(draft.getDescription())
                .systemPrompt(draft.getSystemPrompt())
                .triggers(draft.getTriggers())
                .priority(draft.getPriority());

        // 从 ToolRegistry 关联工具实例
        for (String toolName : draft.getToolNames()) {
            toolRegistry.get(toolName).ifPresent(builder::addTool);
        }

        promptEngine.registerSkill(builder.build());
    }

    private void loadActiveSkills() {
        try {
            List<SkillDraft> activeSkills = skillRepository.findActive();
            for (SkillDraft draft : activeSkills) {
                registerToPromptEngine(draft);
                logger.info("Loaded active skill from DB: {}", draft.getName());
            }
            logger.info("Loaded {} active skills from database", activeSkills.size());
        } catch (Exception e) {
            logger.warn("Failed to load active skills: {}", e.getMessage());
        }
    }
}
