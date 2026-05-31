package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolAlias — alias delegation to primary ClientTool")
class ClientToolAliasTest {

    @Test
    @DisplayName("getName() returns the alias name, not the primary name")
    void nameIsAlias() {
        TestClientTool primary = createPrimary();
        ClientToolAlias alias = new ClientToolAlias("goto", primary);

        assertEquals("goto", alias.getName());
        assertNotEquals(alias.getName(), primary.getName());
    }

    @Test
    @DisplayName("getDescription() delegates to primary")
    void descriptionDelegatesToPrimary() {
        TestClientTool primary = createPrimary();
        ClientToolAlias alias = new ClientToolAlias("goto", primary);

        assertEquals(primary.getDescription(), alias.getDescription());
    }

    @Test
    @DisplayName("getSchema() delegates to primary — same shape returned")
    void schemaDelegatesToPrimary() {
        TestClientTool primary = createPrimary();
        ClientToolAlias alias = new ClientToolAlias("goto", primary);

        assertEquals(primary.getSchema().toMap(), alias.getSchema().toMap());
    }

    @Test
    @DisplayName("execute() delegates to primary and returns its result")
    void executeDelegatesToPrimary() {
        TestClientTool primary = createPrimary();
        ClientToolAlias alias = new ClientToolAlias("goto", primary);
        Map<String, Object> args = Map.of("destination", "home");
        ToolResult result = alias.execute(args);

        assertFalse(result.isError());
    }

    @Test
    @DisplayName("isAutoExecute() delegates to primary")
    void autoExecuteDelegatesToPrimary() {
        TestClientTool primary = createPrimary();
        ClientToolAlias alias = new ClientToolAlias("goto", primary);

        assertEquals(primary.isAutoExecute(), alias.isAutoExecute());
    }

    @Test
    @DisplayName("getPrimary() returns the primary tool")
    void getPrimaryReturnsPrimary() {
        TestClientTool primary = createPrimary();
        ClientToolAlias alias = new ClientToolAlias("goto", primary);

        assertSame(primary, alias.getPrimary());
    }

    @Test
    @DisplayName("null aliasName throws NullPointerException")
    void nullAliasNameThrows() {
        TestClientTool primary = createPrimary();
        assertThrows(NullPointerException.class, () -> new ClientToolAlias(null, primary));
    }

    @Test
    @DisplayName("blank aliasName throws IllegalArgumentException")
    void blankAliasNameThrows() {
        TestClientTool primary = createPrimary();
        assertThrows(IllegalArgumentException.class, () -> new ClientToolAlias("  ", primary));
    }

    @Test
    @DisplayName("null primary throws NullPointerException")
    void nullPrimaryThrows() {
        assertThrows(NullPointerException.class, () -> new ClientToolAlias("goto", null));
    }

    private TestClientTool createPrimary() {
        return new TestClientTool();
    }

    @com.lightweightai.kernel.agent.annotation.ClientTool(
            toolName = "navigate",
            downAction = "startNavigation",
            upAction = "navigationResult",
            namespace = "GeoInformation"
    )
    private static class TestClientTool extends ClientTool<Void> {
        private static final ClientToolDispatcher NOOP_DISPATCHER =
                (callId, toolName, directive) -> CompletableFuture.completedFuture(ToolResult.success("ok"));

        TestClientTool() {
            super(NOOP_DISPATCHER);
        }

        @Override
        protected Map<String, Object> buildPayload(Map<String, Object> args) {
            return args;
        }

        @Override
        protected Void parseResult(ToolResult result) {
            return null;
        }
    }
}
