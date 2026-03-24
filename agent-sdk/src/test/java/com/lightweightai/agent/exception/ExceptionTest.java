package com.lightweightai.agent.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Agent Exception Classes")
class ExceptionTest {

    @Nested
    @DisplayName("AgentException")
    class AgentExceptionTests {

        @Test
        @DisplayName("should construct with message")
        void constructWithMessage() {
            AgentException ex = new AgentException("something went wrong");
            assertEquals("something went wrong", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("should construct with message and cause")
        void constructWithMessageAndCause() {
            Throwable cause = new RuntimeException("root cause");
            AgentException ex = new AgentException("wrapper", cause);
            assertEquals("wrapper", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("should construct with cause only")
        void constructWithCause() {
            Throwable cause = new IllegalStateException("bad state");
            AgentException ex = new AgentException(cause);
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("should be a RuntimeException")
        void isRuntimeException() {
            AgentException ex = new AgentException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    @Nested
    @DisplayName("ConfigException")
    class ConfigExceptionTests {

        @Test
        @DisplayName("should construct with message only")
        void constructWithMessage() {
            ConfigException ex = new ConfigException("missing key");
            assertEquals("missing key", ex.getMessage());
            assertNull(ex.getConfigKey());
        }

        @Test
        @DisplayName("should construct with message and config key")
        void constructWithMessageAndConfigKey() {
            ConfigException ex = new ConfigException("invalid value", "api.key");
            assertEquals("api.key", ex.getConfigKey());
        }

        @Test
        @DisplayName("should append config key to message")
        void getMessageAppendsConfigKey() {
            ConfigException ex = new ConfigException("invalid value", "api.key");
            String message = ex.getMessage();
            assertTrue(message.contains("invalid value"));
            assertTrue(message.contains("[config=api.key]"));
        }

        @Test
        @DisplayName("should not append config key when null")
        void getMessageWithoutConfigKey() {
            ConfigException ex = new ConfigException("missing config");
            assertEquals("missing config", ex.getMessage());
        }

        @Test
        @DisplayName("should construct with message and cause")
        void constructWithMessageAndCause() {
            Throwable cause = new RuntimeException("io error");
            ConfigException ex = new ConfigException("failed to load", cause);
            assertSame(cause, ex.getCause());
            assertNull(ex.getConfigKey());
        }

        @Test
        @DisplayName("should construct with message, config key, and cause")
        void constructWithAllParams() {
            Throwable cause = new RuntimeException("io error");
            ConfigException ex = new ConfigException("failed", "db.url", cause);
            assertEquals("db.url", ex.getConfigKey());
            assertSame(cause, ex.getCause());
            assertTrue(ex.getMessage().contains("[config=db.url]"));
        }

        @Test
        @DisplayName("should be an AgentException")
        void isAgentException() {
            ConfigException ex = new ConfigException("test");
            assertInstanceOf(AgentException.class, ex);
        }
    }

    @Nested
    @DisplayName("LLMException")
    class LLMExceptionTests {

        @Test
        @DisplayName("should construct with message only")
        void constructWithMessage() {
            LLMException ex = new LLMException("api error");
            assertEquals("api error", ex.getMessage());
            assertNull(ex.getProvider());
            assertNull(ex.getStatusCode());
        }

        @Test
        @DisplayName("should construct with message and cause")
        void constructWithMessageAndCause() {
            Throwable cause = new RuntimeException("timeout");
            LLMException ex = new LLMException("failed", cause);
            assertSame(cause, ex.getCause());
            assertNull(ex.getProvider());
            assertNull(ex.getStatusCode());
        }

        @Test
        @DisplayName("should construct with provider and status code")
        void constructWithProviderAndStatusCode() {
            LLMException ex = new LLMException("rate limited", "claude", 429);
            assertEquals("claude", ex.getProvider());
            assertEquals(429, ex.getStatusCode());
        }

        @Test
        @DisplayName("should append provider and status code to message")
        void getMessageAppendsFields() {
            LLMException ex = new LLMException("error", "openai", 500);
            String message = ex.getMessage();
            assertTrue(message.contains("error"));
            assertTrue(message.contains("[provider=openai]"));
            assertTrue(message.contains("[statusCode=500]"));
        }

        @Test
        @DisplayName("should not append null provider or status code")
        void getMessageWithNullFields() {
            LLMException ex = new LLMException("error");
            assertEquals("error", ex.getMessage());
        }

        @Test
        @DisplayName("should construct with all parameters including cause")
        void constructWithAllParams() {
            Throwable cause = new RuntimeException("network");
            LLMException ex = new LLMException("failed", "claude", 503, cause);
            assertEquals("claude", ex.getProvider());
            assertEquals(503, ex.getStatusCode());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("should be an AgentException")
        void isAgentException() {
            LLMException ex = new LLMException("test");
            assertInstanceOf(AgentException.class, ex);
        }
    }

    @Nested
    @DisplayName("PluginException")
    class PluginExceptionTests {

        @Test
        @DisplayName("should construct with message only")
        void constructWithMessage() {
            PluginException ex = new PluginException("plugin error");
            assertEquals("plugin error", ex.getMessage());
            assertNull(ex.getPluginName());
            assertNull(ex.getFunctionName());
        }

        @Test
        @DisplayName("should construct with message and cause")
        void constructWithMessageAndCause() {
            Throwable cause = new RuntimeException("npe");
            PluginException ex = new PluginException("failed", cause);
            assertSame(cause, ex.getCause());
            assertNull(ex.getPluginName());
            assertNull(ex.getFunctionName());
        }

        @Test
        @DisplayName("should construct with plugin name and function name")
        void constructWithPluginAndFunction() {
            PluginException ex = new PluginException("exec error", "mathPlugin", "add");
            assertEquals("mathPlugin", ex.getPluginName());
            assertEquals("add", ex.getFunctionName());
        }

        @Test
        @DisplayName("should append plugin and function to message")
        void getMessageAppendsFields() {
            PluginException ex = new PluginException("failed", "myPlugin", "doStuff");
            String message = ex.getMessage();
            assertTrue(message.contains("failed"));
            assertTrue(message.contains("[plugin=myPlugin]"));
            assertTrue(message.contains("[function=doStuff]"));
        }

        @Test
        @DisplayName("should not append null fields to message")
        void getMessageWithNullFields() {
            PluginException ex = new PluginException("error");
            assertEquals("error", ex.getMessage());
        }

        @Test
        @DisplayName("should construct with all parameters including cause")
        void constructWithAllParams() {
            Throwable cause = new RuntimeException("internal");
            PluginException ex = new PluginException("failed", "p1", "f1", cause);
            assertEquals("p1", ex.getPluginName());
            assertEquals("f1", ex.getFunctionName());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("should be an AgentException")
        void isAgentException() {
            PluginException ex = new PluginException("test");
            assertInstanceOf(AgentException.class, ex);
        }
    }
}
