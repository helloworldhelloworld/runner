package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolAlias — alias view delegating to primary ClientTool")
class ClientToolAliasTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        void rejectsNullAliasName() {
            StubClientTool primary = new StubClientTool();
            assertThrows(NullPointerException.class, () -> new ClientToolAlias(null, primary));
        }

        @Test
        void rejectsNullPrimary() {
            assertThrows(NullPointerException.class, () -> new ClientToolAlias("alias", null));
        }

        @Test
        void rejectsBlankAliasName() {
            StubClientTool primary = new StubClientTool();
            assertThrows(IllegalArgumentException.class, () -> new ClientToolAlias("  ", primary));
        }
    }

    @Nested
    @DisplayName("Delegation to primary")
    class Delegation {

        @Test
        void getNameReturnsAliasNotPrimaryName() {
            StubClientTool primary = new StubClientTool();
            ClientToolAlias alias = new ClientToolAlias("my_alias", primary);
            assertEquals("my_alias", alias.getName());
            assertNotEquals(primary.getName(), alias.getName());
        }

        @Test
        void getDescriptionDelegatesToPrimary() {
            StubClientTool primary = new StubClientTool();
            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            assertEquals(primary.getDescription(), alias.getDescription());
        }

        @Test
        void getSchemaDelegatesToPrimary() {
            StubClientTool primary = new StubClientTool();
            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            assertSame(primary.getSchema(), alias.getSchema());
        }

        @Test
        void executeDelegatesToPrimary() {
            StubClientTool primary = new StubClientTool();
            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            Map<String, Object> args = Map.of("key", "val");
            ToolResult result = alias.execute(args);
            assertNotNull(result);
            assertFalse(result.isError());
        }

        @Test
        void isAutoExecuteDelegatesToPrimary() {
            StubClientTool primary = new StubClientTool();
            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            assertEquals(primary.isAutoExecute(), alias.isAutoExecute());
        }

        @Test
        void getDirectiveDescriptorDelegatesToPrimary() {
            StubClientTool primary = new StubClientTool();
            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            assertSame(primary.getDirectiveDescriptor(), alias.getDirectiveDescriptor());
        }
    }

    @Test
    @DisplayName("getPrimary returns the original tool")
    void getPrimaryReturnsOriginal() {
        StubClientTool primary = new StubClientTool();
        ClientToolAlias alias = new ClientToolAlias("alias", primary);
        assertSame(primary, alias.getPrimary());
    }

    @com.lightweightai.kernel.agent.annotation.ClientTool(
        toolName = "stub_tool",
        downAction = "StubDown",
        upAction = "StubUp",
        namespace = "Test",
        timeoutMs = 1000,
        version = "1.0"
    )
    static class StubClientTool extends ClientTool<String> {

        private static final ClientToolDispatcher NOOP_DISPATCHER =
            (callId, toolName, directive) -> CompletableFuture.completedFuture(ToolResult.success("stub"));

        StubClientTool() {
            super(NOOP_DISPATCHER);
        }

        @Override
        protected Map<String, Object> buildPayload(Map<String, Object> args) {
            return args;
        }

        @Override
        protected String parseResult(ToolResult result) {
            return result.getContent();
        }
    }
}
