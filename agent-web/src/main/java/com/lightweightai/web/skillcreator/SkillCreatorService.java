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
    private final ToolSchemaRepository toolSchemaRepository;

    /** per-session AgentLoop + draft state */
    private final Map<String, AgentLoop> agentLoops = new ConcurrentHashMap<>();
    private final Map<String, SkillDraft> sessionDrafts = new ConcurrentHashMap<>();
    private final InMemoryMemoryProvider memoryProvider = new InMemoryMemoryProvider();

    public SkillCreatorService(LLMProvider llmProvider,
                                ToolRegistry toolRegistry,
                                PromptEngine promptEngine,
                                SkillRepository skillRepository,
                                ToolSchemaRepository toolSchemaRepository) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.promptEngine = promptEngine;
        this.skillRepository = skillRepository;
        this.toolSchemaRepository = toolSchemaRepository;

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
        sb.append("""
            你是一个 Skill 创建助手。通过对话帮助用户定义一个完整的 Skill。

            ## Skill 结构规范

            一个完整的 Skill 包含以下要素：

            ### 基础信息
            - **name**: 唯一标识名（英文，kebab-case，如 `celia-navigation`）
            - **description**: 简短中文描述
            - **version**: 版本号（如 `1.0.0`）
            - **category**: 分类（工具类 / 对话类 / 分析类）
            - **scope**: 作用域（对话内可用）
            - **allowedTools**: 允许调用的工具执行器列表（如 `["FunctionExec"]`）

            ### systemPrompt（核心 — Markdown 格式，参照下方模板）

            systemPrompt 必须包含以下完整结构：

            ```markdown
            # 技能说明
            简要说明技能功能和执行机制。

            ---

            # 触发条件
            | 触发 | 关键词/意图 |
            |------|------------|
            | 触发 | 关键词1、关键词2、... |
            | 不触发 | 不应触发的场景描述 |

            ---

            # 核心决策逻辑

            ## 意图分类
            | 用户意图 | 调用工具 | 示例 |
            |---------|---------|------|
            | 意图A | ToolA | "示例输入" |
            | 意图B | ToolB → ToolC | "示例输入" |

            ## 执行流程
            用流程图或分步骤描述工具调用顺序、分支逻辑、澄清策略。
            > **强制规则**：用引用块标注必须遵守的约束。

            ---

            # 工具定义

            ## ToolA — 工具描述
            说明工具能力边界。

            ```json
            {
              "functionName": "ToolA",
              "parameters": {
                "type": "object",
                "properties": { ... },
                "required": [...]
              }
            }
            ```

            ---

            # 异常处理
            | 异常情况 | 处理策略 |
            |---------|---------|
            | 工具返回空结果 | 告知用户，建议替代方案 |
            | 多候选结果 | 展示列表，等待用户确认 |
            ```

            ### 其他字段
            - **toolNames**: 关联的工具列表（填写 functionName）
            - **triggers**: 触发词列表（用户消息包含这些词时自动激活）
            - **priority**: 优先级（1-100，越小越高，默认 10）

            """);

        // 可用工具列表（来自 ToolRegistry - 已注册的运行时工具）
        sb.append("## 已注册的运行时工具\n\n");
        Collection<Tool> tools = toolRegistry.getEnabled();
        if (tools.isEmpty()) {
            sb.append("（暂无已注册工具）\n");
        } else {
            for (Tool tool : tools) {
                sb.append("- **").append(tool.getName()).append("**: ").append(tool.getDescription());
                if (tool instanceof ToolMetadata meta) {
                    if (meta.getCategory() != null) sb.append(" [分类: ").append(meta.getCategory()).append("]");
                }
                sb.append("\n");
            }
        }

        // 候选工具 Schema（从 Excel 导入）
        sb.append("\n## 候选工具 Schema（从 Excel 导入）\n\n");
        List<ToolSchemaEntry> candidateTools = toolSchemaRepository.findEnabled();
        if (candidateTools.isEmpty()) {
            sb.append("（暂无候选工具 Schema，请先通过 Excel 导入）\n");
        } else {
            String currentCategory = null;
            for (ToolSchemaEntry entry : candidateTools) {
                if (entry.getCategory() != null && !entry.getCategory().equals(currentCategory)) {
                    currentCategory = entry.getCategory();
                    sb.append("\n### ").append(currentCategory).append("\n\n");
                }
                sb.append("- **").append(entry.getName()).append("**: ").append(entry.getDescription() != null ? entry.getDescription() : "");
                if (entry.getInputSchemaJson() != null && !entry.getInputSchemaJson().isBlank()) {
                    sb.append("\n  - Schema: `").append(entry.getInputSchemaJson()).append("`");
                }
                sb.append("\n");
            }
        }

        sb.append("""

            ## 对话引导规则

            1. 首先问用户想创建什么类型的 Skill（工具类/对话类/分析类）
            2. 了解核心功能和涉及的工具
            3. 梳理意图分类和决策逻辑（何时调用哪个工具）
            4. 定义工具的 JSON Schema 参数
            5. 编写异常处理策略
            6. 建议合理的触发词
            7. 每轮对话后，在回复末尾用 <skill_draft> 标签附上当前完整定义

            ## 输出格式

            在每次回复末尾，如果有信息更新，附上完整 skill 定义：
            <skill_draft>
            {
              "name": "skill-name",
              "description": "中文描述",
              "version": "1.0.0",
              "category": "工具类",
              "systemPrompt": "# 技能说明\\n\\n...完整 Markdown...",
              "toolNames": ["ToolA", "ToolB"],
              "triggers": ["关键词1", "关键词2"],
              "priority": 10
            }
            </skill_draft>

            **重要**：
            - systemPrompt 必须是完整的 Markdown 文档，包含触发条件表格、核心决策逻辑、工具定义 JSON Schema、异常处理
            - 工具定义中的 JSON Schema 要详细描述每个参数的类型、含义、是否必填
            - 决策逻辑要用流程图或步骤描述清楚分支和约束

            请用中文与用户对话，保持友好和专业。
            """);

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

        // 从 ToolRegistry 关联运行时工具实例
        for (String toolName : draft.getToolNames()) {
            // 先从运行时 ToolRegistry 查找
            Optional<Tool> runtimeTool = toolRegistry.get(toolName);
            if (runtimeTool.isPresent()) {
                builder.addTool(runtimeTool.get());
            } else {
                // 从候选工具 Schema 数据库查找，作为 ToolDefinition 加入
                toolSchemaRepository.findByName(toolName).ifPresent(entry -> {
                    Map<String, Object> params = new HashMap<>();
                    if (entry.getInputSchemaJson() != null && !entry.getInputSchemaJson().isBlank()) {
                        try {
                            params = MAPPER.readValue(entry.getInputSchemaJson(), Map.class);
                        } catch (Exception e) {
                            logger.warn("Failed to parse input schema for tool '{}': {}", toolName, e.getMessage());
                        }
                    }
                    builder.addTool(toolName, entry.getDescription() != null ? entry.getDescription() : "", params);
                });
            }
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
