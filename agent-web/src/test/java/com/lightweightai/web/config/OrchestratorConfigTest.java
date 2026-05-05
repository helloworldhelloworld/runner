package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolResult;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.orchestrator.AgentFactory;
import com.lightweightai.kernel.orchestrator.Orchestrator;
import com.lightweightai.kernel.orchestrator.SubagentRuntime;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import com.lightweightai.web.skillcreator.InMemoryMemoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrchestratorConfig — Spring wiring for multi-agent orchestration")
class OrchestratorConfigTest {

    private OrchestratorConfig config;
    private ToolRegistry toolRegistry;
    private LLMProvider llmProvider;
    private MemoryProvider memoryProvider;
    private Orchestrator orchestrator;

    @BeforeEach
    void setup() throws Exception {
        config = new OrchestratorConfig();
        // Inject @Value field that Spring would normally populate
        var field = OrchestratorConfig.class.getDeclaredField("maxConcurrentSubagents");
        field.setAccessible(true);
        field.setInt(config, 3);

        toolRegistry = new ToolRegistry();
        toolRegistry.register(stubTool("echo"));
        llmProvider = CapturingLLMProvider.endTurn("done");
        memoryProvider = new InMemoryMemoryProvider();
    }

    @AfterEach
    void teardown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }

    @Nested
    @DisplayName("agentRegistry bean")
    class AgentRegistryBean {

        @Test
        @DisplayName("registers 'default' and 'worker' agents")
        void registersTwoAgents() {
            AgentRegistry registry = config.agentRegistry();
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("'default' agent has maxSpawnDepth=1")
        void defaultAgentCanSpawn() {
            AgentRegistry registry = config.agentRegistry();
            Optional<AgentProfile> profile = registry.get("default");
            assertTrue(profile.isPresent());
            assertEquals(1, profile.get().getMaxSpawnDepth());
        }

        @Test
        @DisplayName("'worker' agent has maxSpawnDepth=0 — cannot spawn subagents")
        void workerCannotSpawn() {
            AgentRegistry registry = config.agentRegistry();
            Optional<AgentProfile> profile = registry.get("worker");
            assertTrue(profile.isPresent());
            assertEquals(0, profile.get().getMaxSpawnDepth());
        }

        @Test
        @DisplayName("default agent is set to 'default'")
        void defaultAgentIsDefault() {
            AgentRegistry registry = config.agentRegistry();
            Optional<AgentProfile> defaultProfile = registry.getDefault();
            assertTrue(defaultProfile.isPresent());
            assertEquals("default", defaultProfile.get().getAgentId());
        }
    }

    @Nested
    @DisplayName("agentFactory bean")
    class AgentFactoryBean {

        @Test
        @DisplayName("creates AgentFactory with injected dependencies")
        void createsFactory() {
            AgentFactory factory = config.agentFactory(llmProvider, memoryProvider, toolRegistry);
            assertNotNull(factory);
            assertSame(memoryProvider, factory.getSharedMemory());
        }
    }

    @Nested
    @DisplayName("orchestratorChatHandler bean")
    class OrchestratorBean {

        @Test
        @DisplayName("returns a ChatHandler that is an Orchestrator instance")
        void returnsChatHandlerOrchestrator() {
            AgentRegistry registry = config.agentRegistry();
            AgentFactory factory = config.agentFactory(llmProvider, memoryProvider, toolRegistry);
            SubagentRuntime runtime = config.subagentRuntime(factory, registry);

            ChatHandler handler = config.orchestratorChatHandler(registry, factory, runtime, toolRegistry);
            orchestrator = (Orchestrator) handler;

            assertNotNull(handler);
            assertInstanceOf(Orchestrator.class, handler);
        }

        @Test
        @DisplayName("registers wait_subagent and list_subagents tools in global ToolRegistry")
        void registersSubagentTools() {
            AgentRegistry registry = config.agentRegistry();
            AgentFactory factory = config.agentFactory(llmProvider, memoryProvider, toolRegistry);
            SubagentRuntime runtime = config.subagentRuntime(factory, registry);

            ChatHandler handler = config.orchestratorChatHandler(registry, factory, runtime, toolRegistry);
            orchestrator = (Orchestrator) handler;

            assertTrue(toolRegistry.has("wait_subagent"),
                    "wait_subagent tool should be registered in ToolRegistry");
            assertTrue(toolRegistry.has("list_subagents"),
                    "list_subagents tool should be registered in ToolRegistry");
        }

        @Test
        @DisplayName("tool definitions include subagent tools — verifies wiring chain")
        void toolDefinitionsIncludeSubagentTools() {
            AgentRegistry registry = config.agentRegistry();
            AgentFactory factory = config.agentFactory(llmProvider, memoryProvider, toolRegistry);
            SubagentRuntime runtime = config.subagentRuntime(factory, registry);

            ChatHandler handler = config.orchestratorChatHandler(registry, factory, runtime, toolRegistry);
            orchestrator = (Orchestrator) handler;

            var toolDefs = toolRegistry.getToolDefinitions();
            boolean hasWait = toolDefs.stream()
                    .anyMatch(d -> "wait_subagent".equals(d.get("name")));
            boolean hasList = toolDefs.stream()
                    .anyMatch(d -> "list_subagents".equals(d.get("name")));
            boolean hasEcho = toolDefs.stream()
                    .anyMatch(d -> "echo".equals(d.get("name")));

            assertTrue(hasWait, "Tool definitions should include wait_subagent");
            assertTrue(hasList, "Tool definitions should include list_subagents");
            assertTrue(hasEcho, "Tool definitions should include original echo tool");
        }
    }

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() {
                return new ToolSchema(Map.of("type", "object", "properties", Map.of()));
            }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
