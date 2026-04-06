package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.DynamicPluginProvider;
import com.lightweightai.kernel.agent.LegacyScanProvider;
import com.lightweightai.kernel.agent.ManualToolProvider;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSourceProvider;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.trace.Tracer;
import com.lightweightai.kernel.trace.export.ConsoleSpanExporter;
import com.lightweightai.kernel.trace.export.StreamEventSpanExporter;
import com.lightweightai.kernel.instruction.InstructionRegistry;
import com.lightweightai.kernel.instruction.ProviderAdapterFactory;
import com.lightweightai.kernel.instruction.claude.ClaudeSkillAdapter;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.ResilientLLMProvider;
import com.lightweightai.kernel.llm.claude.ClaudeProvider;
import com.lightweightai.kernel.llm.claude.ClaudeProProvider;
import com.lightweightai.kernel.llm.openrouter.OpenAIWebSocketProvider;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.skill.Skill;
import com.lightweightai.kernel.skill.SkillLoader;
import com.lightweightai.kernel.speech.SpeechProvider;
import com.lightweightai.kernel.speech.AzureSpeechProvider;
import com.lightweightai.kernel.speech.OpenAISpeechProvider;
import com.lightweightai.tools.client.GetPositionTool;
import com.lightweightai.tools.web.WebTools;
import com.lightweightai.web.skillcreator.SkillCreatorService;
import com.lightweightai.web.skillcreator.SkillRepository;
import com.lightweightai.web.skillcreator.SkillTestCaseRepository;
import com.lightweightai.web.skillcreator.ToolSchemaRepository;
import com.lightweightai.web.websocket.SessionAwareClientToolDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Agent configuration
 */
@Configuration
public class AgentConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);

    @Value("${app.mock-mode:true}")
    private boolean mockMode;

    @Value("${app.provider-type:mock}")
    private String providerType;

    @Value("${app.claude.api-key:}")
    private String claudeApiKey;

    @Value("${app.claude.model:claude-3-5-sonnet-20241022}")
    private String claudeModel;

    @Value("${app.claude.session-key:}")
    private String claudeSessionKey;

    @Value("${app.claude.organization-id:}")
    private String claudeOrgId;

    @Value("${app.openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${app.openrouter.model:anthropic/claude-3.5-sonnet}")
    private String openRouterModel;

    @Value("${app.openrouter.base-url:}")
    private String openRouterBaseUrl;

    @Value("${app.ws.url:}")
    private String wsLlmUrl;

    @Value("${app.ws.api-key:}")
    private String wsLlmApiKey;

    @Value("${app.ws.model:gpt-4}")
    private String wsLlmModel;

    @Value("${app.tools-enabled:true}")
    private boolean toolsEnabled;

    @Value("${app.web-search.api-key:}")
    private String webSearchApiKey;

    @Value("${app.web-search.max-results:3}")
    private int webSearchMaxResults;

    @Value("${app.speech.provider:openai}")
    private String speechProviderType;

    @Value("${app.speech.azure.api-key:}")
    private String azureSpeechKey;

    @Value("${app.speech.azure.region:eastasia}")
    private String azureSpeechRegion;

    @Value("${app.speech.azure.voice:zh-CN-XiaoxiaoNeural}")
    private String azureSpeechVoice;

    @Value("${app.speech.openai.api-key:}")
    private String openaiSpeechKey;

    @Value("${app.speech.openai.voice:nova}")
    private String openaiSpeechVoice;

    @Value("${app.skill-creator.db-path:./data/skills.db}")
    private String skillCreatorDbPath;

    @Bean
    public DynamicLLMProvider llmProvider() {
        String type = providerType.toLowerCase();

        // auto模式：按优先级自动检测可用的API Key
        if ("auto".equals(type)) {
            type = detectProviderType();
            logger.info("Auto-detected provider type: {}", type);
        }

        LLMProvider inner;
        // mock 模式不需要包裹 ResilientLLMProvider
        if ("mock".equals(type)) {
            logger.warn("Using Mock LLM Provider — frontend will receive fake responses. " +
                "Set ANTHROPIC_API_KEY or OPENROUTER_API_KEY for real LLM responses.");
            inner = new MockLLMProvider();
        } else {
            LLMProvider rawProvider = createProvider(type);
            // 包裹弹性层：自动重试 + 健康状态跟踪
            inner = new ResilientLLMProvider(rawProvider, 2, 1000);
        }
        return new DynamicLLMProvider(inner);
    }

    /**
     * Build a new LLMProvider from explicit configuration parameters.
     * Used by ModelConfigController for runtime reconfiguration.
     */
    public LLMProvider buildProvider(String type, String apiKey, String model, String baseUrl) {
        try {
            switch (type.toLowerCase()) {
                case "openrouter":
                    if ((apiKey == null || apiKey.isBlank()) && (baseUrl == null || baseUrl.isBlank())) {
                        throw new IllegalArgumentException("OpenRouter 需要 API Key 或 Base URL");
                    }
                    return new ResilientLLMProvider(
                        new OpenRouterProvider(apiKey, model, baseUrl), 2, 1000);
                case "api":
                    if (apiKey == null || apiKey.isBlank()) {
                        throw new IllegalArgumentException("Claude API 需要 API Key");
                    }
                    return new ResilientLLMProvider(
                        new ClaudeProvider(apiKey, model != null && !model.isBlank() ? model : "claude-3-5-sonnet-20241022"), 2, 1000);
                case "ws":
                    if (baseUrl == null || baseUrl.isBlank()) {
                        throw new IllegalArgumentException("WebSocket 模式需要填写 WS 地址");
                    }
                    return new ResilientLLMProvider(
                        new OpenAIWebSocketProvider(baseUrl, model, apiKey), 2, 1000);
                case "mock":
                    return new MockLLMProvider();
                default:
                    throw new IllegalArgumentException("不支持的 Provider 类型: " + type);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("创建 Provider 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建原始 LLM Provider。
     * 配置有误时记录 ERROR 日志并 fallback 到 mock，而不是抛异常阻止启动。
     */
    private LLMProvider createProvider(String type) {
        try {
            switch (type) {
                case "api":
                    if (isBlank(claudeApiKey)) {
                        logger.error("API mode requires ANTHROPIC_API_KEY — falling back to mock. " +
                            "Set the environment variable or use PROVIDER_TYPE=mock.");
                        return new MockLLMProvider();
                    }
                    logger.info("Using Claude API Provider with model: {}", claudeModel);
                    return new ClaudeProvider(claudeApiKey, claudeModel);

                case "openrouter":
                    if (isBlank(openRouterApiKey) && isBlank(openRouterBaseUrl)) {
                        logger.error("OpenRouter mode requires OPENROUTER_API_KEY or OPENROUTER_BASE_URL — falling back to mock.");
                        return new MockLLMProvider();
                    }
                    logger.info("Using OpenRouter Provider with model: {}, baseUrl: {}",
                        openRouterModel,
                        !isBlank(openRouterBaseUrl) ? openRouterBaseUrl : "(default)");
                    return new OpenRouterProvider(openRouterApiKey, openRouterModel, openRouterBaseUrl);

                case "ws":
                    if (isBlank(wsLlmUrl)) {
                        logger.error("WS mode requires WS_LLM_URL — falling back to mock.");
                        return new MockLLMProvider();
                    }
                    logger.info("Using OpenAI WebSocket Provider: url={}, model={}", wsLlmUrl, wsLlmModel);
                    return new OpenAIWebSocketProvider(wsLlmUrl, wsLlmModel, wsLlmApiKey);

                case "pro":
                    if (isBlank(claudeSessionKey)) {
                        logger.error("Pro mode requires CLAUDE_SESSION_KEY — falling back to mock.");
                        return new MockLLMProvider();
                    }
                    logger.info("Using Claude Pro Provider with session key");
                    return new ClaudeProProvider(claudeSessionKey, claudeOrgId);

                default:
                    logger.error("Unknown provider type: '{}' — falling back to mock. " +
                        "Valid values: auto, api, openrouter, ws, pro, mock", type);
                    return new MockLLMProvider();
            }
        } catch (Exception e) {
            logger.error("Failed to create LLM provider [{}]: {} — falling back to mock.",
                type, e.getMessage(), e);
            return new MockLLMProvider();
        }
    }

    /**
     * 按优先级自动检测可用的LLM Provider：
     * 1. OPENROUTER_BASE_URL（自定义网关）→ openrouter（用户显式配了自建网关，最高优先）
     * 2. ANTHROPIC_API_KEY → api
     * 3. OPENROUTER_API_KEY → openrouter
     * 4. CLAUDE_SESSION_KEY → pro
     * 5. 全部为空 → mock（并警告）
     */
    private String detectProviderType() {
        // WebSocket LLM URL 最高优先级：用户明确指定了 WebSocket 网关
        if (!isBlank(wsLlmUrl)) {
            return "ws";
        }
        // 自定义 base-url 优先级最高：用户明确指定了自建 LLM 网关
        if (!isBlank(openRouterBaseUrl)) {
            return "openrouter";
        }
        if (!isBlank(claudeApiKey)) {
            return "api";
        }
        if (!isBlank(openRouterApiKey)) {
            return "openrouter";
        }
        if (!isBlank(claudeSessionKey)) {
            return "pro";
        }
        logger.warn("No API keys detected — falling back to mock mode. " +
            "Set ANTHROPIC_API_KEY, OPENROUTER_API_KEY, or CLAUDE_SESSION_KEY for real LLM responses.");
        return "mock";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ─── 心灵港湾 SoulHarbor — 系统提示（OpenClaw 基础提示，Skills 可在此之上叠加）────
    private static final String SOUL_HARBOR_BASE_PROMPT =
        "你是「心灵港湾」⚓ 的引导者，一位温暖、富有同理心的倾听者。\n" +
        "\n" +
        "你的使命：\n" +
        "- 用心倾听每一个来访者的心声\n" +
        "- 提供温暖、包容、不评判的陪伴\n" +
        "- 帮助他们理清思路，找到内心的答案\n" +
        "- 给予希望和力量，而不是简单的建议\n" +
        "\n" +
        "你的风格：\n" +
        "- 语气温和、真诚、不做作\n" +
        "- 善于提出启发性的问题\n" +
        "- 尊重对方的感受和选择\n" +
        "- 回应简洁而有力量（通常 2-4 句话）\n" +
        "\n" +
        "重要原则：\n" +
        "1. 永远不要轻视对方的感受\n" +
        "2. 不要给出具体行动建议（除非对方明确请求）\n" +
        "3. 帮助对方自己找到答案，而不是替他们决定\n" +
        "4. 当对方情绪激动时，先接纳情绪，再探讨问题\n" +
        "5. 你拥有完整的对话记忆，请自然引用之前的对话内容\n" +
        "6. 若对话记忆中提到用户姓名、过去的困扰、情绪模式，请主动运用这些信息建立连接";

    /**
     * PromptEngine Bean（OpenClaw 核心，管理 Skill + 记忆检索 + Prompt 组装）
     *
     * 基础提示：SOUL_HARBOR_BASE_PROMPT
     * 注册 Skill：soul-comfort（核心）、emotion-guide（情绪支持）、sleep-helper（睡眠辅导）
     * MemoryProvider：FileMemoryAdapter（桥接 FileMemoryManager + ConversationMemory）
     */
    @Bean
    public PromptEngine promptEngine(FileMemoryAdapter fileMemoryAdapter, ToolRegistry toolRegistry) {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(fileMemoryAdapter)
            .baseSystemPrompt(SOUL_HARBOR_BASE_PROMPT)
            .build();

        // 核心心灵引导 Skill（始终激活，priority=0）
        engine.registerSkill(com.lightweightai.kernel.prompt.Skill.builder()
            .name("soul-comfort")
            .description("心灵引导：倾听与共情")
            .systemPrompt("") // 基础提示已覆盖，此 Skill 不额外追加
            .priority(0)
            .build());

        // 情绪支持 Skill（触发词：焦虑、害怕、担心、紧张、压力、崩溃、无助）
        engine.registerSkill(com.lightweightai.kernel.prompt.Skill.builder()
            .name("emotion-guide")
            .description("情绪支持与调节")
            .systemPrompt(
                "当用户表达焦虑、恐惧或压力时：\n" +
                "- 先验证并接纳他们的情绪（「这种感觉完全可以理解」）\n" +
                "- 引导他们说出具体是什么让他们感到害怕或焦虑\n" +
                "- 可以温和介绍「注意力转移」「腹式呼吸」等简单方法\n" +
                "- 不要急于给出解决方案"
            )
            .triggers(List.of("焦虑", "害怕", "担心", "紧张", "压力", "崩溃", "无助", "恐惧"))
            .priority(1)
            .build());

        // 睡眠辅导 Skill（触发词：失眠、睡不着、睡眠、做梦）
        engine.registerSkill(com.lightweightai.kernel.prompt.Skill.builder()
            .name("sleep-helper")
            .description("睡眠困扰辅导")
            .systemPrompt(
                "当用户提到失眠或睡眠问题时：\n" +
                "- 先了解他们睡眠困扰的具体情况（入睡困难、多梦、早醒？）\n" +
                "- 探索可能的原因（压力、焦虑、作息？）\n" +
                "- 可以提及睡前放松技巧，但重点是倾听和理解，不是给教程\n" +
                "- 若问题持续严重，温和建议考虑专业帮助"
            )
            .triggers(List.of("失眠", "睡不着", "睡眠", "做梦", "噩梦", "入睡"))
            .priority(2)
            .build());

        // 天气查询 Skill（触发词匹配后激活，调用端工具 GetPosition + 云工具 checkWeather）
        com.lightweightai.kernel.prompt.Skill.Builder weatherBuilder = com.lightweightai.kernel.prompt.Skill.builder()
            .name("weather")
            .description("天气查询")
            .systemPrompt(
                "当用户询问天气时：\n" +
                "1. 先调用 GetPosition 获取用户的GPS位置和城市名\n" +
                "2. 再调用 checkWeather 传入位置信息查询天气\n" +
                "3. 自然地告知用户天气信息\n" +
                "如果 GetPosition 失败，请直接询问用户所在城市，然后调用 checkWeather。"
            )
            .triggers(List.of("天气", "weather", "forecast", "预报", "气温", "下雨"))
            .priority(3);
        // 从 ToolRegistry 关联工具实例（Skill 激活时 LLM 才可见）
        toolRegistry.get("GetPosition").ifPresent(weatherBuilder::addTool);
        toolRegistry.get("checkWeather").ifPresent(weatherBuilder::addTool);
        engine.registerSkill(weatherBuilder.build());

        logger.info("PromptEngine initialized with {} skills: {}",
            engine.getRegisteredSkills().size(), engine.getRegisteredSkills());
        return engine;
    }

    @Bean
    public SessionAwareClientToolDispatcher sessionAwareClientToolDispatcher() {
        return new SessionAwareClientToolDispatcher();
    }

    @Bean
    public ToolRegistry toolRegistry(SessionAwareClientToolDispatcher clientToolDispatcher) {
        ToolRegistry registry = new ToolRegistry();

        if (!toolsEnabled) {
            logger.warn("Tools disabled (app.tools-enabled=false). " +
                "LLM requests will not include function calling definitions. " +
                "Set TOOLS_ENABLED=true or remove the setting to enable tools.");
            return registry;
        }

        // 通过 ToolSourceProvider 组装工具来源
        List<ToolSourceProvider> providers = List.of(
            // 1. SPI 扫描（现有逻辑包装）
            new LegacyScanProvider(tool -> !tool.getName().equals("web_search")),
            // 2. 手动注册（需要构造参数 / 运行时绑定的工具）
            new ManualToolProvider()
                .addAnnotated(new WebTools(webSearchApiKey, webSearchMaxResults))
                .addTool(new GetPositionTool(clientToolDispatcher)),
            // 3. 动态插件（监听 plugins/ 目录，支持热加载 jar）
            dynamicPluginProvider()
        );

        providers.forEach(p -> {
            p.start(registry);
            logger.info("ToolSourceProvider started: {} → registry now has {} tools",
                p.sourceType(), registry.size());
        });

        logger.info("ToolRegistry initialized: {}", registry);
        return registry;
    }

    @Bean
    public DynamicPluginProvider dynamicPluginProvider() {
        return new DynamicPluginProvider(java.nio.file.Path.of("plugins"));
    }

    @Bean
    public ToolExecutor toolExecutor(ToolRegistry toolRegistry) {
        ToolExecutor executor = new ToolExecutor(toolRegistry);
        logger.info("ToolExecutor initialized with {} tools", executor.getFunctionCount());
        return executor;
    }

    @Bean
    public StreamEventSpanExporter streamEventSpanExporter() {
        return new StreamEventSpanExporter();
    }

    @Bean
    public Tracer tracer(StreamEventSpanExporter streamEventSpanExporter) {
        return Tracer.builder()
            .addExporter(new ConsoleSpanExporter())
            .addExporter(streamEventSpanExporter)
            .build();
    }

    @Bean
    public ToolCallingLoop toolCallingLoop(LLMProvider provider, ToolExecutor executor, Tracer tracer) {
        return ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(executor)
            .maxIterations(10)
            .tracer(tracer)
            .build();
    }

    @Bean
    public InstructionRegistry instructionRegistry() {
        InstructionRegistry registry = new InstructionRegistry(
            ProviderAdapterFactory.claude()
        );

        // Load example skills
        try {
            String skillsPath = Paths.get("example-skills").toAbsolutePath().toString();
            logger.info("Loading skills from: {}", skillsPath);

            Map<String, Skill> skills = SkillLoader.loadSkills(Paths.get(skillsPath));
            skills.values().forEach(skill -> {
                registry.registerPackage(new ClaudeSkillAdapter(skill));
                logger.info("Registered skill: {}", skill.getName());
            });
        } catch (Exception e) {
            logger.warn("Failed to load skills: {}", e.getMessage());
            // Create a demo skill
            createDemoSkill(registry);
        }

        return registry;
    }

    private void createDemoSkill(InstructionRegistry registry) {
        Skill demoSkill = Skill.builder()
            .name("demo-skill")
            .description("A demo skill for testing")
            .instructions("Always respond in a friendly and professional manner.")
            .build();

        registry.registerPackage(new ClaudeSkillAdapter(demoSkill));
        logger.info("Created demo skill");
    }

    @Bean
    public SkillRepository skillRepository() {
        // 确保数据目录存在
        java.nio.file.Path dbPath = java.nio.file.Path.of(skillCreatorDbPath);
        try {
            java.nio.file.Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            logger.warn("Failed to create skill-creator db directory: {}", e.getMessage());
        }
        return new SkillRepository(skillCreatorDbPath);
    }

    @Bean
    public SkillTestCaseRepository skillTestCaseRepository() {
        return new SkillTestCaseRepository(skillCreatorDbPath);
    }

    @Bean
    public ToolSchemaRepository toolSchemaRepository() {
        java.nio.file.Path dbPath = java.nio.file.Path.of(skillCreatorDbPath);
        try {
            java.nio.file.Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            logger.warn("Failed to create tool-schema db directory: {}", e.getMessage());
        }
        // 复用同一个 skills.db 数据库文件
        return new ToolSchemaRepository(skillCreatorDbPath);
    }

    @Bean
    public SkillCreatorService skillCreatorService(LLMProvider llmProvider,
                                                     ToolRegistry toolRegistry,
                                                     PromptEngine promptEngine,
                                                     SkillRepository skillRepository,
                                                     ToolSchemaRepository toolSchemaRepository) {
        return new SkillCreatorService(llmProvider, toolRegistry, promptEngine, skillRepository, toolSchemaRepository);
    }

    // ==================== Skill Pipeline Beans ====================

    @Bean
    public com.lightweightai.web.skillcreator.TestRunRepository testRunRepository() {
        return new com.lightweightai.web.skillcreator.TestRunRepository(skillCreatorDbPath);
    }

    @Bean
    public com.lightweightai.web.skillcreator.SkillVersionRepository skillVersionRepository() {
        return new com.lightweightai.web.skillcreator.SkillVersionRepository(skillCreatorDbPath);
    }

    @Bean
    public com.lightweightai.web.skillcreator.SimulationRepository simulationRepository() {
        return new com.lightweightai.web.skillcreator.SimulationRepository(skillCreatorDbPath);
    }

    @Bean
    public com.lightweightai.web.skillcreator.SkillTestExecutionService skillTestExecutionService(
            LLMProvider llmProvider,
            SkillRepository skillRepository,
            SkillTestCaseRepository skillTestCaseRepository,
            com.lightweightai.web.skillcreator.TestRunRepository testRunRepository) {
        com.lightweightai.kernel.core.TestExecutor testExecutor =
            new com.lightweightai.kernel.core.DefaultTestExecutor(llmProvider);
        return new com.lightweightai.web.skillcreator.SkillTestExecutionService(
            testExecutor, skillRepository, skillTestCaseRepository, testRunRepository);
    }

    @Bean
    public com.lightweightai.web.skillcreator.SkillPublishService skillPublishService(
            SkillRepository skillRepository,
            com.lightweightai.web.skillcreator.SkillVersionRepository skillVersionRepository,
            com.lightweightai.web.skillcreator.TestRunRepository testRunRepository,
            PromptEngine promptEngine,
            ToolRegistry toolRegistry,
            com.lightweightai.kernel.instruction.InstructionRegistry instructionRegistry) {
        return new com.lightweightai.web.skillcreator.SkillPublishService(
            skillRepository, skillVersionRepository, testRunRepository, promptEngine, toolRegistry, instructionRegistry);
    }

    @Bean
    public com.lightweightai.web.skillcreator.SkillSimulationService skillSimulationService(
            LLMProvider llmProvider,
            com.lightweightai.web.skillcreator.SkillVersionRepository skillVersionRepository,
            com.lightweightai.web.skillcreator.SimulationRepository simulationRepository) {
        return new com.lightweightai.web.skillcreator.SkillSimulationService(
            llmProvider, skillVersionRepository, simulationRepository);
    }

    @Bean
    public com.lightweightai.web.skillcreator.SkillTestCaseGenerationService skillTestCaseGenerationService(
            LLMProvider llmProvider,
            SkillRepository skillRepository,
            SkillTestCaseRepository skillTestCaseRepository) {
        return new com.lightweightai.web.skillcreator.SkillTestCaseGenerationService(
            llmProvider, skillRepository, skillTestCaseRepository);
    }

    @Bean
    public SpeechProvider speechProvider() {
        String provider = speechProviderType.toLowerCase();

        switch (provider) {
            case "azure":
                if (azureSpeechKey == null || azureSpeechKey.isEmpty()) {
                    logger.warn("Azure Speech key not configured, falling back to Mock");
                    return new MockSpeechProvider();
                }
                logger.info("Using Azure Speech Services (region: {}, voice: {})", azureSpeechRegion, azureSpeechVoice);
                return new AzureSpeechProvider(azureSpeechKey, azureSpeechRegion, azureSpeechVoice);

            case "openai":
                if (openaiSpeechKey == null || openaiSpeechKey.isEmpty()) {
                    logger.error("OpenAI Speech requires OPENAI_SPEECH_KEY environment variable");
                    logger.error("Note: OPENROUTER_API_KEY cannot be used for OpenAI Speech API");
                    logger.error("Please set: export OPENAI_SPEECH_KEY=sk-proj-xxx");
                    logger.warn("Falling back to Mock Speech Provider");
                    return new MockSpeechProvider();
                }

                // Validate API key format
                if (openaiSpeechKey.startsWith("sk-or-")) {
                    logger.error("Invalid API key: OPENAI_SPEECH_KEY appears to be an OpenRouter key (sk-or-*)");
                    logger.error("OpenAI Speech requires a real OpenAI API key (sk-proj-* or sk-*)");
                    logger.error("Get your key at: https://platform.openai.com/api-keys");
                    logger.warn("Falling back to Mock Speech Provider");
                    return new MockSpeechProvider();
                }

                logger.info("Using OpenAI Speech (Whisper + TTS, voice: {})", openaiSpeechVoice);
                return new OpenAISpeechProvider(openaiSpeechKey, openaiSpeechVoice);

            case "mock":
            default:
                logger.info("Using Mock Speech Provider");
                return new MockSpeechProvider();
        }
    }

    /**
     * Mock LLM Provider for testing without API key
     */
    private static class MockLLMProvider implements LLMProvider {
        @Override
        public com.lightweightai.kernel.llm.LLMResponse complete(
            java.util.List<com.lightweightai.kernel.llm.ConversationMessage> messages,
            com.lightweightai.kernel.llm.LLMOptions options
        ) {
            String lastMessage = messages.get(messages.size() - 1).getTextContent();

            return com.lightweightai.kernel.llm.LLMResponse.builder()
                .message(com.lightweightai.kernel.llm.ConversationMessage.builder()
                    .role(com.lightweightai.kernel.llm.ConversationMessage.MessageRole.ASSISTANT)
                    .textContent("Mock response: I received your message: \"" + lastMessage + "\"")
                    .build())
                .build();
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeAsync(
            java.util.List<com.lightweightai.kernel.llm.ConversationMessage> messages,
            com.lightweightai.kernel.llm.LLMOptions options
        ) {
            return java.util.concurrent.CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeStream(
            java.util.List<com.lightweightai.kernel.llm.ConversationMessage> messages,
            com.lightweightai.kernel.llm.LLMOptions options,
            com.lightweightai.kernel.llm.LLMProvider.StreamEventHandler handler
        ) {
            com.lightweightai.kernel.llm.LLMResponse response = complete(messages, options);
            if (handler != null) {
                handler.onStart();
                handler.onTextDelta("Mock");
                handler.onTextDelta(" streaming");
                handler.onTextDelta(" response");
                handler.onComplete(response);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(response);
        }

        @Override
        public com.lightweightai.kernel.llm.ModelCapability getModelCapability() {
            return null;
        }

        @Override
        public String getProviderName() {
            return "mock";
        }
    }

    /**
     * Mock Speech Provider for testing without API key
     */
    private static class MockSpeechProvider implements SpeechProvider {
        private static final Logger mockLogger = LoggerFactory.getLogger(MockSpeechProvider.class);

        @Override
        public java.util.concurrent.CompletableFuture<String> recognizeAsync(java.io.InputStream audioStream, String audioFormat) {
            mockLogger.warn("Using Mock Speech Recognition - Please configure OPENAI_SPEECH_KEY or AZURE_SPEECH_KEY");
            return java.util.concurrent.CompletableFuture.completedFuture("这是模拟的语音识别结果");
        }

        @Override
        public java.util.concurrent.CompletableFuture<byte[]> synthesizeAsync(String text, String emotion) {
            mockLogger.error("Mock Speech Provider cannot generate audio - Please configure OPENAI_SPEECH_KEY or AZURE_SPEECH_KEY");
            // Return failed future with clear error message
            return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException(
                    "语音合成需要配置真实的 API Key。请设置 OPENAI_SPEECH_KEY 或 AZURE_SPEECH_KEY 环境变量。\n" +
                    "详情请查看: agent-web/.env.example"
                )
            );
        }

        @Override
        public String getProviderName() {
            return "mock-speech";
        }
    }
}
