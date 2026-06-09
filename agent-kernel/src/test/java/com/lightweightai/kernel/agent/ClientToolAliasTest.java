package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ClientToolAlias — alias view delegating to primary ClientTool")
class ClientToolAliasTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        void rejectsNullAliasName() {
            ClientTool<?> primary = mock(ClientTool.class);
            assertThrows(NullPointerException.class, () -> new ClientToolAlias(null, primary));
        }

        @Test
        void rejectsNullPrimary() {
            assertThrows(NullPointerException.class, () -> new ClientToolAlias("alias", null));
        }

        @Test
        void rejectsBlankAliasName() {
            ClientTool<?> primary = mock(ClientTool.class);
            assertThrows(IllegalArgumentException.class, () -> new ClientToolAlias("  ", primary));
        }
    }

    @Nested
    @DisplayName("Delegation to primary")
    class Delegation {

        @Test
        void getNameReturnsAliasNotPrimaryName() {
            ClientTool<?> primary = mock(ClientTool.class);
            when(primary.getName()).thenReturn("primary_tool");

            ClientToolAlias alias = new ClientToolAlias("my_alias", primary);
            assertEquals("my_alias", alias.getName());
        }

        @Test
        void getDescriptionDelegatesToPrimary() {
            ClientTool<?> primary = mock(ClientTool.class);
            when(primary.getDescription()).thenReturn("Primary description");

            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            assertEquals("Primary description", alias.getDescription());
        }

        @Test
        void getSchemaDelegatesToPrimary() {
            ClientTool<?> primary = mock(ClientTool.class);
            ToolSchema schema = ToolSchema.empty();
            when(primary.getSchema()).thenReturn(schema);

            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            assertSame(schema, alias.getSchema());
        }

        @Test
        void executeDelegatesToPrimary() {
            ClientTool<?> primary = mock(ClientTool.class);
            ToolResult expected = ToolResult.success("result");
            when(primary.execute(any())).thenReturn(expected);

            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            Map<String, Object> args = Map.of("key", "val");
            ToolResult result = alias.execute(args);

            assertSame(expected, result);
            verify(primary).execute(args);
        }

        @Test
        void isAutoExecuteDelegatesToPrimary() {
            ClientTool<?> primary = mock(ClientTool.class);
            when(primary.isAutoExecute()).thenReturn(true);

            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            assertTrue(alias.isAutoExecute());
        }

        @Test
        void getDirectiveDescriptorDelegatesToPrimary() {
            ClientTool<?> primary = mock(ClientTool.class);
            DirectiveDescriptor descriptor = mock(DirectiveDescriptor.class);
            when(primary.getDirectiveDescriptor()).thenReturn(descriptor);

            ClientToolAlias alias = new ClientToolAlias("alias", primary);
            assertSame(descriptor, alias.getDirectiveDescriptor());
        }
    }

    @Test
    @DisplayName("getPrimary returns the original tool")
    void getPrimaryReturnsOriginal() {
        ClientTool<?> primary = mock(ClientTool.class);
        ClientToolAlias alias = new ClientToolAlias("alias", primary);
        assertSame(primary, alias.getPrimary());
    }
}
