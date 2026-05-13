package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolAlias — delegation and naming")
class ClientToolAliasTest {

    @Test
    @DisplayName("alias getName() returns alias name, not primary name")
    void aliasNameOverridesPrimary() {
        StubClientTool primary = new StubClientTool("primaryTool");
        ClientToolAlias alias = new ClientToolAlias("aliasName", primary);

        assertEquals("aliasName", alias.getName());
        assertNotEquals(primary.getName(), alias.getName());
    }

    @Test
    @DisplayName("description delegates to primary")
    void descriptionDelegatesToPrimary() {
        StubClientTool primary = new StubClientTool("tool");
        ClientToolAlias alias = new ClientToolAlias("alias", primary);

        assertEquals(primary.getDescription(), alias.getDescription());
    }

    @Test
    @DisplayName("schema delegates to primary")
    void schemaDelegatesToPrimary() {
        StubClientTool primary = new StubClientTool("tool");
        ClientToolAlias alias = new ClientToolAlias("alias", primary);

        assertSame(primary.getSchema(), alias.getSchema());
    }

    @Test
    @DisplayName("execute delegates to primary")
    void executeDelegatesToPrimary() {
        StubClientTool primary = new StubClientTool("tool");
        ClientToolAlias alias = new ClientToolAlias("alias", primary);

        ToolResult result = alias.execute(Map.of("key", "val"));

        assertNotNull(result);
        assertTrue(primary.executeCalled);
    }

    @Test
    @DisplayName("isAutoExecute delegates to primary")
    void autoExecuteDelegatesToPrimary() {
        StubClientTool primary = new StubClientTool("tool");
        ClientToolAlias alias = new ClientToolAlias("alias", primary);

        assertEquals(primary.isAutoExecute(), alias.isAutoExecute());
    }

    @Test
    @DisplayName("getPrimary() returns the wrapped tool")
    void getPrimaryReturnsWrapped() {
        StubClientTool primary = new StubClientTool("tool");
        ClientToolAlias alias = new ClientToolAlias("alias", primary);

        assertSame(primary, alias.getPrimary());
    }

    @Test
    @DisplayName("null aliasName throws NullPointerException")
    void nullAliasNameThrows() {
        StubClientTool primary = new StubClientTool("tool");
        assertThrows(NullPointerException.class, () -> new ClientToolAlias(null, primary));
    }

    @Test
    @DisplayName("blank aliasName throws IllegalArgumentException")
    void blankAliasNameThrows() {
        StubClientTool primary = new StubClientTool("tool");
        assertThrows(IllegalArgumentException.class, () -> new ClientToolAlias("  ", primary));
    }

    @Test
    @DisplayName("null primary throws NullPointerException")
    void nullPrimaryThrows() {
        assertThrows(NullPointerException.class, () -> new ClientToolAlias("alias", null));
    }

    // ==================== Helpers ====================

    private static class StubClientTool implements Tool {
        private final String name;
        boolean executeCalled = false;

        StubClientTool(String name) { this.name = name; }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return "stub desc"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override
        public ToolResult execute(Map<String, Object> args) {
            executeCalled = true;
            return ToolResult.success("stub result");
        }
        @Override public boolean isAutoExecute() { return false; }
    }
}
