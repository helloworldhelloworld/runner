package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ClientTool;
import com.lightweightai.kernel.agent.directive.ClientToolAliasRegistry;
import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolAlias — alias view delegates to primary ClientTool")
class ClientToolAliasTest {

    @AfterEach
    void resetGlobalState() {
        ClientToolAliasRegistry.reset();
    }

    @Test
    @DisplayName("alias name is the alias, not the primary")
    void aliasNameIsDistinct() {
        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        ClientToolAlias alias = new ClientToolAlias("MyAlias", primary);

        assertEquals("MyAlias", alias.getName());
        assertNotEquals(primary.getName(), alias.getName());
    }

    @Test
    @DisplayName("description, schema, autoExecute delegate to primary")
    void delegatesToPrimary() {
        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        ClientToolAlias alias = new ClientToolAlias("Alt", primary);

        assertEquals(primary.getDescription(), alias.getDescription());
        assertEquals(primary.getSchema().toMap(), alias.getSchema().toMap());
        assertEquals(primary.isAutoExecute(), alias.isAutoExecute());
    }

    @Test
    @DisplayName("execute delegates to primary and returns same result")
    void executeRoutesToPrimary() {
        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        ClientToolAlias alias = new ClientToolAlias("Alt", primary);

        ToolResult result = alias.execute(Map.of("key", "value"));
        assertFalse(result.isError());
    }

    @Test
    @DisplayName("getPrimary returns the original ClientTool")
    void getPrimaryReturnsPrimary() {
        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        ClientToolAlias alias = new ClientToolAlias("Alt", primary);

        assertSame(primary, alias.getPrimary());
    }

    @Test
    @DisplayName("getDirectiveDescriptor delegates to primary")
    void directiveDescriptorComesFromPrimary() {
        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        ClientToolAlias alias = new ClientToolAlias("Alt", primary);

        DirectiveDescriptor dd = alias.getDirectiveDescriptor();
        assertEquals("TestNs", dd.getNamespace());
        assertEquals("DoDown", dd.getDownAction());
    }

    @Test
    @DisplayName("constructor rejects null aliasName")
    void rejectsNullName() {
        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        assertThrows(NullPointerException.class, () -> new ClientToolAlias(null, primary));
    }

    @Test
    @DisplayName("constructor rejects blank aliasName")
    void rejectsBlankName() {
        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        assertThrows(IllegalArgumentException.class, () -> new ClientToolAlias("  ", primary));
    }

    @Test
    @DisplayName("constructor rejects null primary")
    void rejectsNullPrimary() {
        assertThrows(NullPointerException.class, () -> new ClientToolAlias("Alt", null));
    }

    @Test
    @DisplayName("ClientTool.getAliasTools returns alias views from descriptor")
    void clientToolExposesAliases() {
        ClientToolAliasRegistry.setSource(name ->
            "FakeTool".equals(name) ? List.of("Alias1", "Alias2") : List.of());

        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        List<Tool> aliases = primary.getAliasTools();

        assertEquals(2, aliases.size());
        assertEquals("Alias1", aliases.get(0).getName());
        assertEquals("Alias2", aliases.get(1).getName());
        assertTrue(aliases.get(0) instanceof ClientToolAlias);
        assertSame(primary, ((ClientToolAlias) aliases.get(0)).getPrimary());
    }

    @Test
    @DisplayName("ClientTool with no aliases returns empty list")
    void noAliasesReturnsEmptyList() {
        ClientToolAliasRegistry.setSource(name -> List.of());
        FakeClientTool primary = new FakeClientTool(noopDispatcher());

        assertTrue(primary.getAliasTools().isEmpty());
    }

    @Test
    @DisplayName("alias registered in ToolRegistry is LLM-visible and executable")
    void aliasFlowsThroughToolRegistry() {
        ClientToolAliasRegistry.setSource(name ->
            "FakeTool".equals(name) ? List.of("RegAlias") : List.of());

        ToolRegistry registry = new ToolRegistry();
        FakeClientTool primary = new FakeClientTool(noopDispatcher());
        registry.register(primary);

        assertTrue(registry.has("FakeTool"), "primary must be registered");
        assertTrue(registry.has("RegAlias"), "alias must be registered");

        Tool aliasTool = registry.get("RegAlias").orElseThrow();
        ToolResult result = aliasTool.execute(Map.of());
        assertFalse(result.isError(), "alias execution must succeed");
    }

    @Test
    @DisplayName("alias tool definitions appear in LLM definitions list")
    void aliasAppearsInToolDefinitions() {
        ClientToolAliasRegistry.setSource(name ->
            "FakeTool".equals(name) ? List.of("LlmAlias") : List.of());

        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeClientTool(noopDispatcher()));

        List<Map<String, Object>> defs = registry.getToolDefinitions();
        boolean hasAlias = defs.stream()
            .anyMatch(d -> "LlmAlias".equals(d.get("name")));
        assertTrue(hasAlias, "alias tool must appear in LLM tool definitions");
    }

    // ==================== Test fixtures ====================

    @ClientTool(toolName = "FakeTool", downAction = "DoDown", upAction = "DoUp",
        namespace = "TestNs", timeoutMs = 1000, version = "1.0")
    private static class FakeClientTool extends com.lightweightai.kernel.agent.ClientTool<Map<String, Object>> {
        FakeClientTool(ClientToolDispatcher dispatcher) {
            super(dispatcher);
        }

        @Override
        protected Map<String, Object> buildPayload(Map<String, Object> args) {
            return Map.of();
        }

        @Override
        protected Map<String, Object> parseResult(ToolResult result) {
            return Map.of("status", "ok");
        }
    }

    private static ClientToolDispatcher noopDispatcher() {
        return (callId, toolName, directive) -> CompletableFuture.completedFuture(
            ToolResult.success("ok", Map.of(
                "header", Map.of("namespace", "TestNs", "name", "DoUp"),
                "payload", Map.of()
            ))
        );
    }
}
