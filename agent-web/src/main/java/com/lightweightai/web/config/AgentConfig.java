package com.lightweightai.web.config;

import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.instruction.InstructionRegistry;
import com.lightweightai.kernel.instruction.ProviderAdapterFactory;
import com.lightweightai.kernel.instruction.claude.ClaudeSkillAdapter;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.claude.ClaudeProvider;
import com.lightweightai.kernel.llm.claude.ClaudeProProvider;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.skill.Skill;
import com.lightweightai.kernel.skill.SkillLoader;
import com.lightweightai.kernel.speech.SpeechProvider;
import com.lightweightai.kernel.speech.AzureSpeechProvider;
import com.lightweightai.kernel.speech.OpenAISpeechProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
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

    @Bean
    public LLMProvider llmProvider() {
        // 优先使用provider-type配置
        String type = providerType.toLowerCase();

        // 如果是旧配置（mock-mode），兼容处理
        if (mockMode && type.equals("mock")) {
            logger.info("Using Mock LLM Provider (no API key configured)");
            return new MockLLMProvider();
        }

        switch (type) {
            case "pro":
                if (claudeSessionKey == null || claudeSessionKey.isEmpty()) {
                    logger.warn("Pro mode selected but no session key provided, falling back to Mock");
                    return new MockLLMProvider();
                }
                logger.info("Using Claude Pro Provider with session key");
                return new ClaudeProProvider(claudeSessionKey, claudeOrgId);

            case "api":
                if (claudeApiKey == null || claudeApiKey.isEmpty()) {
                    logger.warn("API mode selected but no API key provided, falling back to Mock");
                    return new MockLLMProvider();
                }
                logger.info("Using Claude API Provider with model: {}", claudeModel);
                return new ClaudeProvider(claudeApiKey, claudeModel);

            case "openrouter":
                if (openRouterApiKey == null || openRouterApiKey.isEmpty()) {
                    logger.warn("OpenRouter mode selected but no API key provided, falling back to Mock");
                    return new MockLLMProvider();
                }
                logger.info("Using OpenRouter Provider with model: {}", openRouterModel);
                return new OpenRouterProvider(openRouterApiKey, openRouterModel);

            case "mock":
            default:
                logger.info("Using Mock LLM Provider");
                return new MockLLMProvider();
        }
    }

    @Bean
    public ToolExecutor toolExecutor() {
        ToolExecutor executor = new ToolExecutor();

        // Register example tools using anonymous classes
        executor.registerFunction("add", new com.lightweightai.kernel.plugin.PluginFunction() {
            @Override
            public String getName() { return "add"; }

            @Override
            public String getDescription() { return "Add two numbers"; }

            @Override
            public java.util.List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() {
                return java.util.List.of();
            }

            @Override
            public FunctionResult execute(Map<String, Object> input) {
                int a = ((Number) input.get("a")).intValue();
                int b = ((Number) input.get("b")).intValue();
                return FunctionResult.success(String.valueOf(a + b));
            }

            @Override
            public Map<String, Object> toJsonSchema() {
                return Map.of("name", "add", "description", "Add two numbers");
            }
        });

        executor.registerFunction("multiply", new com.lightweightai.kernel.plugin.PluginFunction() {
            @Override
            public String getName() { return "multiply"; }

            @Override
            public String getDescription() { return "Multiply two numbers"; }

            @Override
            public java.util.List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() {
                return java.util.List.of();
            }

            @Override
            public FunctionResult execute(Map<String, Object> input) {
                int a = ((Number) input.get("a")).intValue();
                int b = ((Number) input.get("b")).intValue();
                return FunctionResult.success(String.valueOf(a * b));
            }

            @Override
            public Map<String, Object> toJsonSchema() {
                return Map.of("name", "multiply", "description", "Multiply two numbers");
            }
        });

        executor.registerFunction("get_time", new com.lightweightai.kernel.plugin.PluginFunction() {
            @Override
            public String getName() { return "get_time"; }

            @Override
            public String getDescription() { return "Get current time"; }

            @Override
            public java.util.List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() {
                return java.util.List.of();
            }

            @Override
            public FunctionResult execute(Map<String, Object> input) {
                return FunctionResult.success(java.time.LocalDateTime.now().toString());
            }

            @Override
            public Map<String, Object> toJsonSchema() {
                return Map.of("name", "get_time", "description", "Get current time");
            }
        });

        logger.info("Registered {} tools", executor.getFunctionCount());
        return executor;
    }

    @Bean
    public ToolCallingLoop toolCallingLoop(LLMProvider provider, ToolExecutor executor) {
        return ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(executor)
            .maxIterations(10)
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
            if (handler != null) {
                handler.onStart();
                handler.onTextDelta("Mock");
                handler.onTextDelta(" streaming");
                handler.onTextDelta(" response");
            }
            return java.util.concurrent.CompletableFuture.completedFuture(complete(messages, options));
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
