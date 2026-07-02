package com.lightweightai.agent;

import com.lightweightai.agent.exception.AgentException;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance test: DefaultAgent.chat() → ToolCallingLoop → LLMProvider wiring.
 *
 * Covers critical gap: the #1 untested path where the SDK's public API
 * (Agent.builder().build().chat()) reaches the kernel's ToolCallingLoop and LLMProvider.
 *
 * Uses a test-only SPI provider ("test") registered via ServiceLoader to avoid HTTP calls.
 */
@DisplayName("DefaultAgent.chat() → Kernel wiring")
class DefaultAgentChatWiringTest {

    @BeforeEach
    void clearSpy() {
        TestLLMProviderSpi.LAST_INSTANCE.set(null);
    }

    @Nested
    @DisplayName("Happy path: chat() reaches LLM provider")
    class HappyPathTests {

        @Test
        @DisplayName("chat() returns response text from LLM provider")
        void chatReturnsResponseText() {
            Agent agent = buildTestAgent();
            String response = agent.chat("What is 2+2?");

            assertEquals("test response", response);
        }

        @Test
        @DisplayName("chat() sends user message to LLM provider")
        void chatSendsUserMessage() {
            Agent agent = buildTestAgent();
            agent.chat("Hello world");

            TestLLMProviderSpi.TestProvider provider = TestLLMProviderSpi.LAST_INSTANCE.get();
            assertNotNull(provider, "Test provider should have been created");
            assertEquals(1, provider.capturedMessages.size());

            List<ConversationMessage> messages = provider.capturedMessages.get(0);
            boolean hasUserMessage = messages.stream()
                    .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.USER
                            && "Hello world".equals(m.getTextContent()));
            assertTrue(hasUserMessage, "LLM should receive the user message");
        }

        @Test
        @DisplayName("ensureInitialized() creates provider only once across multiple chat() calls")
        void ensureInitializedCreatesProviderOnce() {
            Agent agent = buildTestAgent();
            agent.chat("first");
            agent.chat("second");

            TestLLMProviderSpi.TestProvider provider = TestLLMProviderSpi.LAST_INSTANCE.get();
            assertNotNull(provider);
            assertEquals(2, provider.capturedMessages.size(),
                    "Same provider instance should be reused for both calls");
        }
    }

    @Nested
    @DisplayName("Tool definitions wiring")
    class ToolDefinitionsTests {

        @Test
        @DisplayName("Tools registered via AgentBuilder.tools() appear in LLMOptions.toolDefinitions")
        void toolsFlowToLLMOptions() {
            ToolRegistry registry = new ToolRegistry();
            registry.register(dummyTool("calculator"));
            registry.register(dummyTool("translator"));

            Agent agent = buildTestAgentWithRegistry(registry);
            agent.chat("use my tools");

            TestLLMProviderSpi.TestProvider provider = TestLLMProviderSpi.LAST_INSTANCE.get();
            assertNotNull(provider);
            assertEquals(1, provider.capturedOptions.size());

            LLMOptions options = provider.capturedOptions.get(0);
            assertNotNull(options.getToolDefinitions(),
                    "toolDefinitions must not be null when tools are registered");
            assertEquals(2, options.getToolDefinitions().size(),
                    "Both tools should appear in toolDefinitions");

            List<String> names = options.getToolDefinitions().stream()
                    .map(d -> (String) d.get("name"))
                    .toList();
            assertTrue(names.contains("calculator"), "calculator tool should be in definitions");
            assertTrue(names.contains("translator"), "translator tool should be in definitions");
        }

        @Test
        @DisplayName("No tools registered → toolDefinitions is empty or null")
        void noToolsMeansNoDefinitions() {
            Agent agent = buildTestAgent();
            agent.chat("no tools here");

            TestLLMProviderSpi.TestProvider provider = TestLLMProviderSpi.LAST_INSTANCE.get();
            assertNotNull(provider);
            LLMOptions options = provider.capturedOptions.get(0);
            assertTrue(options.getToolDefinitions() == null || options.getToolDefinitions().isEmpty(),
                    "toolDefinitions should be empty when no tools are registered");
        }
    }

    @Nested
    @DisplayName("Memory wiring")
    class MemoryTests {

        @Test
        @DisplayName("Multi-turn conversation with memory sends history to LLM")
        void memoryHistorySentToLLM() {
            Agent agent = buildTestAgentWithMemory();

            agent.chat("My name is Alice");
            agent.chat("What is my name?");

            TestLLMProviderSpi.TestProvider provider = TestLLMProviderSpi.LAST_INSTANCE.get();
            assertNotNull(provider);
            assertEquals(2, provider.capturedMessages.size());

            List<ConversationMessage> secondCallMessages = provider.capturedMessages.get(1);
            long userMessages = secondCallMessages.stream()
                    .filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                    .count();
            assertTrue(userMessages >= 2,
                    "Second call should include previous user message in history, got " + userMessages);
        }

        @Test
        @DisplayName("Memory stores user and assistant messages after chat()")
        void memoryStoresMessages() {
            Agent agent = buildTestAgentWithMemory();
            agent.chat("hello");

            assertNotNull(agent.getMemory());
            assertEquals(2, agent.getMemory().getMessageCount(),
                    "Memory should store both user and assistant messages");
        }
    }

    @Nested
    @DisplayName("Error paths")
    class ErrorTests {

        @Test
        @DisplayName("Unknown provider throws AgentException with descriptive message")
        void unknownProviderThrows() {
            Agent agent = buildAgentWithProvider("nonexistent_provider");

            AgentException ex = assertThrows(AgentException.class, () -> agent.chat("hi"));
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("Chat failed") || ex.getMessage().contains("nonexistent_provider"),
                    "Error should mention the failed provider or chat failure: " + ex.getMessage());
        }

        @Test
        @DisplayName("ChatOptions with systemPrompt passes through to LLM messages")
        void systemPromptPassesThrough() {
            Agent agent = buildTestAgent();
            ChatOptions opts = ChatOptions.builder()
                    .systemPrompt("You are a helpful assistant")
                    .build();
            agent.chat("hi", opts);

            TestLLMProviderSpi.TestProvider provider = TestLLMProviderSpi.LAST_INSTANCE.get();
            List<ConversationMessage> messages = provider.capturedMessages.get(0);
            boolean hasSystem = messages.stream()
                    .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM
                            && m.getTextContent().contains("helpful assistant"));
            assertTrue(hasSystem, "System prompt from ChatOptions should reach LLM");
        }
    }

    // ==================== Helpers ====================

    private Agent buildTestAgent() {
        return buildAgentWithProvider("test");
    }

    private Agent buildTestAgentWithMemory() {
        AgentBuilder builder = configureTestProvider(new AgentBuilder());
        builder.withMemory();
        return builder.build();
    }

    private Agent buildTestAgentWithRegistry(ToolRegistry registry) {
        AgentBuilder builder = configureTestProvider(new AgentBuilder());
        builder.tools(registry);
        return builder.build();
    }

    private Agent buildAgentWithProvider(String providerName) {
        AgentBuilder builder = new AgentBuilder();
        setField(builder, "llmProvider", providerName);
        setField(builder, "apiKey", "test-api-key");
        setField(builder, "model", "test-model");
        return builder.build();
    }

    private AgentBuilder configureTestProvider(AgentBuilder builder) {
        setField(builder, "llmProvider", "test");
        setField(builder, "apiKey", "test-api-key");
        setField(builder, "model", "test-model");
        return builder;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    private static Tool dummyTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("result of " + name);
            }
        };
    }
}
