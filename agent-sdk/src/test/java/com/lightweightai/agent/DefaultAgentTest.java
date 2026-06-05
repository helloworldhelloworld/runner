package com.lightweightai.agent;

import com.lightweightai.agent.exception.AgentException;
import com.lightweightai.agent.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultAgent - Agent 默认实现")
class DefaultAgentTest {

    @Nested
    @DisplayName("构建与初始化")
    class ConstructionTests {

        @Test
        @DisplayName("通过 builder 构建 agent")
        void shouldConstructFromBuilder() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();
            assertNotNull(agent);
        }

        @Test
        @DisplayName("不启用记忆时 getMemory 返回 null")
        void shouldReturnNullMemoryWhenDisabled() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();
            assertNull(agent.getMemory());
        }

        @Test
        @DisplayName("启用记忆时 getMemory 不为 null")
        void shouldReturnMemoryWhenEnabled() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .withMemory()
                    .build();
            assertNotNull(agent.getMemory());
        }
    }

    @Nested
    @DisplayName("插件管理")
    class PluginManagementTests {

        @Test
        @DisplayName("构建时注册的插件可通过 getPlugins 获取")
        void shouldReturnRegisteredPlugins() {
            Plugin p1 = new TestPlugin("math");
            Plugin p2 = new TestPlugin("time");

            Agent agent = Agent.builder()
                    .claude("test-key")
                    .plugin(p1)
                    .plugin(p2)
                    .build();

            assertEquals(2, agent.getPlugins().size());
        }

        @Test
        @DisplayName("addPlugin 动态添加插件")
        void shouldAddPluginDynamically() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            assertEquals(0, agent.getPlugins().size());

            agent.addPlugin(new TestPlugin("dynamic"));
            assertEquals(1, agent.getPlugins().size());
            assertEquals("dynamic", agent.getPlugins().get(0).getName());
        }

        @Test
        @DisplayName("addPlugin 支持链式调用")
        void addPluginShouldReturnSelf() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            Agent returned = agent.addPlugin(new TestPlugin("a"));
            assertSame(agent, returned);
        }

        @Test
        @DisplayName("getPlugins 返回副本")
        void getPluginsShouldReturnCopy() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .plugin(new TestPlugin("p"))
                    .build();

            var list = agent.getPlugins();
            list.clear();
            assertEquals(1, agent.getPlugins().size());
        }
    }

    @Nested
    @DisplayName("记忆管理")
    class MemoryManagementTests {

        @Test
        @DisplayName("clearMemory 在未启用记忆时不抛异常")
        void clearMemoryShouldBeNoOpWhenDisabled() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();
            assertDoesNotThrow(agent::clearMemory);
        }

        @Test
        @DisplayName("clearMemory 在启用记忆时清空历史")
        void clearMemoryShouldClearHistory() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .withMemory()
                    .build();

            assertNotNull(agent.getMemory());
            assertEquals(0, agent.getMemory().getMessageCount());

            agent.getMemory().addMessage("user", "hello");
            assertEquals(1, agent.getMemory().getMessageCount());

            agent.clearMemory();
            assertEquals(0, agent.getMemory().getMessageCount());
        }
    }

    @Nested
    @DisplayName("Chat 异常路径")
    class ChatExceptionTests {

        @Test
        @DisplayName("chatStream 抛出 UnsupportedOperationException")
        void chatStreamShouldThrow() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            StreamCallback noop = new StreamCallback() {};
            assertThrows(UnsupportedOperationException.class,
                    () -> agent.chatStream("hello", noop));
        }

        @Test
        @DisplayName("chatStream 带 options 也抛出 UnsupportedOperationException")
        void chatStreamWithOptionsShouldThrow() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();

            ChatOptions opts = ChatOptions.builder().build();
            StreamCallback noop = new StreamCallback() {};
            assertThrows(UnsupportedOperationException.class,
                    () -> agent.chatStream("hello", opts, noop));
        }
    }

    @Nested
    @DisplayName("插件函数注册")
    class PluginFunctionRegistrationTests {

        @Test
        @DisplayName("带函数的插件在构建时注册函数")
        void shouldRegisterPluginFunctions() {
            Plugin calculator = new TestPlugin("calc") {
                {
                    registerFunction("add", args -> {
                        return "result";
                    });
                }
            };

            Agent agent = Agent.builder()
                    .claude("test-key")
                    .plugin(calculator)
                    .build();

            assertEquals(1, agent.getPlugins().size());
            assertEquals(1, agent.getPlugins().get(0).getFunctions().size());
            assertNotNull(agent.getPlugins().get(0).getFunction("add"));
        }
    }

    static class TestPlugin extends Plugin {
        private final String name;

        TestPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "Test plugin: " + name; }
    }
}
