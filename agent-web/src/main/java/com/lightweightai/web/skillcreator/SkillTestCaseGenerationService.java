package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 驱动的测试用例生成服务
 *
 * 多轮对话：基于 Skill 定义生成/追加/修改测试用例。
 * LLM 输出中解析 <test_cases> JSON 块，实时更新用例列表。
 */
public class SkillTestCaseGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(SkillTestCaseGenerationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CASES_PATTERN = Pattern.compile(
            "<test_cases>\\s*(\\[.*?])\\s*</test_cases>", Pattern.DOTALL);

    private final LLMProvider llmProvider;
    private final SkillRepository skillRepository;
    private final SkillTestCaseRepository testCaseRepository;
    private final InMemoryMemoryProvider memoryProvider = new InMemoryMemoryProvider();

    private final Map<String, AgentLoop> agentLoops = new ConcurrentHashMap<>();
    private final Map<String, List<SkillTestCase>> sessionCases = new ConcurrentHashMap<>();

    public SkillTestCaseGenerationService(LLMProvider llmProvider,
                                           SkillRepository skillRepository,
                                           SkillTestCaseRepository testCaseRepository) {
        this.llmProvider = llmProvider;
        this.skillRepository = skillRepository;
        this.testCaseRepository = testCaseRepository;
    }

    /**
     * 流式对话生成用例
     *
     * @param sessionId 会话 ID
     * @param skillId   关联的 Skill ID
     * @param message   用户消息（"生成基础用例"、"补充异常场景"、"修改第3个用例的断言"）
     * @return 流式事件：TEXT_DELTA + POST_PROCESS_DATA("test_cases_update", cases)
     */
    public Flux<StreamEvent> chat(String sessionId, String skillId, String message) {
        SkillDraft skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));

        AgentLoop agentLoop = agentLoops.computeIfAbsent(sessionId,
            sid -> createAgentLoop(sid, skill));

        StringBuilder accumulated = new StringBuilder();

        return agentLoop.runReactive(message, sessionId)
            .doOnNext(event -> {
                if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                    accumulated.append(event.getTextDelta());
                }
            })
            .doOnComplete(() -> parseCases(sessionId, skillId, accumulated.toString()))
            .concatWith(Flux.defer(() -> {
                List<SkillTestCase> cases = sessionCases.get(sessionId);
                if (cases != null && !cases.isEmpty()) {
                    List<Map<String, Object>> caseMaps = cases.stream().map(this::caseToMap).toList();
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("skillId", skillId);
                    data.put("count", cases.size());
                    data.put("cases", caseMaps);
                    return Flux.just(StreamEvent.postProcessData("test_cases_update", data));
                }
                return Flux.empty();
            }));
    }

    /**
     * 确认用例 — 批量保存到数据库
     */
    public List<SkillTestCase> confirmCases(String sessionId) {
        List<SkillTestCase> cases = sessionCases.get(sessionId);
        if (cases == null || cases.isEmpty()) {
            throw new IllegalStateException("No generated cases for session: " + sessionId);
        }
        testCaseRepository.saveAll(cases);
        logger.info("Confirmed {} test cases from session {}", cases.size(), sessionId);
        return cases;
    }

    /**
     * 获取当前 session 的用例列表
     */
    public List<SkillTestCase> getCases(String sessionId) {
        return sessionCases.getOrDefault(sessionId, List.of());
    }

    private AgentLoop createAgentLoop(String sessionId, SkillDraft skill) {
        String systemPrompt = buildSystemPrompt(skill);
        return AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memoryProvider)
            .toolRegistry(new ToolRegistry())
            .systemPrompt(systemPrompt)
            .maxToolIterations(1)
            .build();
    }

    private String buildSystemPrompt(SkillDraft skill) {
        return """
            你是一个 Skill 测试用例生成助手。根据 Skill 定义和用户需求，生成高质量的测试用例。

            ## 当前 Skill 信息

            - **名称**: %s
            - **描述**: %s
            - **工具**: %s
            - **触发词**: %s

            ### SystemPrompt
            ```
            %s
            ```

            ## 输出格式

            每次回复结尾，输出完整的用例列表（包括之前的 + 新增/修改的），用 <test_cases> 标签包裹：

            <test_cases>
            [
              {
                "category": "分类",
                "description": "用例描述",
                "input": "用户输入",
                "assertions": ["断言1", "断言2"],
                "memory": "可选的记忆上下文",
                "mockResponses": {"工具名": {"响应字段": "值"}}
              }
            ]
            </test_cases>

            ## 断言格式规范

            断言必须使用以下格式（工具名与"调用"之间有空格）：
            - `调用 ToolName` — 必须调用某工具
            - `不调用 ToolName` — 不能调用某工具
            - `先调用 ToolA 再调用 ToolB` — 调用顺序
            - `不触发` — 不应调用任何工具

            ## 用例设计原则

            1. **正常路径**: 覆盖所有触发词和意图分类
            2. **边界情况**: 模糊输入、缺少参数、多义性
            3. **异常场景**: 不应触发的场景、错误输入
            4. **多轮对话**: 需要澄清或追问的场景
            5. **工具链**: 需要多个工具协作的场景

            用户可能会说"生成基础用例"、"补充异常场景"、"修改第N个用例"等，根据指令更新用例列表。
            """.formatted(
                skill.getName(),
                skill.getDescription(),
                skill.getToolNames(),
                skill.getTriggers(),
                skill.getSystemPrompt() != null ? skill.getSystemPrompt() : "(无)"
            );
    }

    @SuppressWarnings("unchecked")
    private void parseCases(String sessionId, String skillId, String fullText) {
        Matcher matcher = CASES_PATTERN.matcher(fullText);
        if (matcher.find()) {
            String json = matcher.group(1);
            try {
                List<Map<String, Object>> rawCases = MAPPER.readValue(json, new TypeReference<>() {});
                List<SkillTestCase> cases = new ArrayList<>();
                for (Map<String, Object> raw : rawCases) {
                    SkillTestCase tc = new SkillTestCase();
                    tc.setSkillId(skillId);
                    tc.setCategory((String) raw.getOrDefault("category", ""));
                    tc.setDescription((String) raw.getOrDefault("description", ""));
                    tc.setInput((String) raw.getOrDefault("input", ""));
                    tc.setMemory((String) raw.get("memory"));
                    Object assertions = raw.get("assertions");
                    if (assertions instanceof List) {
                        tc.setAssertions(((List<Object>) assertions).stream()
                            .map(Object::toString).toList());
                    }
                    Object mockResp = raw.get("mockResponses");
                    if (mockResp instanceof Map) {
                        tc.setMockResponses((Map<String, Object>) mockResp);
                    }
                    cases.add(tc);
                }
                sessionCases.put(sessionId, cases);
                logger.info("Parsed {} test cases for session {}", cases.size(), sessionId);
            } catch (Exception e) {
                logger.warn("Failed to parse test_cases JSON: {}", e.getMessage());
            }
        }
    }

    private Map<String, Object> caseToMap(SkillTestCase tc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", tc.getCategory());
        m.put("description", tc.getDescription());
        m.put("input", tc.getInput());
        m.put("assertions", tc.getAssertions());
        if (tc.getMemory() != null) m.put("memory", tc.getMemory());
        if (tc.getMockResponses() != null) m.put("mockResponses", tc.getMockResponses());
        return m;
    }
}
