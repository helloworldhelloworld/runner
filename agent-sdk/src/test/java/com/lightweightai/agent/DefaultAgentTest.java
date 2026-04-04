package com.lightweightai.agent;

import com.lightweightai.agent.exception.AgentException;
import com.lightweightai.agent.memory.ConversationMemory;
import com.lightweightai.agent.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultAgent")
class DefaultAgentTest {

    @Nested
    @DisplayName("Memory Management")
    class MemoryTests {

        @Test
        void shouldReturnNullMemoryWhenNotEnabled() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            assertNull(agent.getMemory());
        }

        @Test
        void shouldReturnMemoryWhenEnabled() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .withMemory()
                    .build();

            assertNotNull(agent.getMemory());
        }

        @Test
        void shouldClearMemorySafely() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .withMemory()
                    .build();

            // Should not throw even with empty memory
            assertDoesNotThrow(() -> agent.clearMemory());
        }

        @Test
        void shouldClearMemorySafelyWhenDisabled() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            // Should not throw when memory is null
            assertDoesNotThrow(() -> agent.clearMemory());
        }

        @Test
        void shouldTrackMessageCountAfterAdd() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .withMemory()
                    .build();

            ConversationMemory memory = agent.getMemory();
            assertEquals(0, memory.getMessageCount());

            memory.addMessage("user", "hello");
            memory.addMessage("assistant", "hi");
            assertEquals(2, memory.getMessageCount());
        }

        @Test
        void shouldClearAllMessages() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .withMemory()
                    .build();

            ConversationMemory memory = agent.getMemory();
            memory.addMessage("user", "hello");
            memory.addMessage("assistant", "hi");
            agent.clearMemory();

            assertEquals(0, memory.getMessageCount());
        }
    }

    @Nested
    @DisplayName("Plugin Management")
    class PluginTests {

        @Test
        void shouldStartWithConfiguredPlugins() {
            TestPlugin p = new TestPlugin("test");
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .plugin(p)
                    .build();

            assertEquals(1, agent.getPlugins().size());
        }

        @Test
        void shouldAddPluginDynamically() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            agent.addPlugin(new TestPlugin("dynamic"));
            assertEquals(1, agent.getPlugins().size());
            assertEquals("dynamic", agent.getPlugins().get(0).getName());
        }

        @Test
        void shouldSupportChainedPluginAddition() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            Agent result = agent.addPlugin(new TestPlugin("p1"));
            assertSame(agent, result);
        }

        @Test
        void shouldReturnDefensiveCopyOfPlugins() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .plugin(new TestPlugin("test"))
                    .build();

            agent.getPlugins().clear();
            assertEquals(1, agent.getPlugins().size());
        }
    }

    @Nested
    @DisplayName("Chat Stream")
    class ChatStreamTests {

        @Test
        void shouldThrowUnsupportedForStream() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            assertThrows(UnsupportedOperationException.class, () ->
                    agent.chatStream("hello", new StreamCallback() {}));
        }

        @Test
        void shouldThrowUnsupportedForStreamWithOptions() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            ChatOptions options = ChatOptions.builder().build();
            assertThrows(UnsupportedOperationException.class, () ->
                    agent.chatStream("hello", options, new StreamCallback() {}));
        }
    }

    static class TestPlugin extends Plugin {
        private final String name;

        TestPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Test plugin";
        }
    }
}
