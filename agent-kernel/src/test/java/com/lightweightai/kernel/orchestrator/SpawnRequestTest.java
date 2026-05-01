package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - Subagent spawn 请求参数")
class SpawnRequestTest {

    @Nested
    @DisplayName("Builder 验证")
    class BuilderValidationTests {

        @Test
        @DisplayName("必填字段完整时正常构建")
        void shouldBuildWithRequiredFields() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1")
                    .task("搜索文档")
                    .build();

            assertEquals("agent:worker:main:s1", request.getParentSessionKey());
            assertEquals("搜索文档", request.getTask());
            assertNull(request.getAgentId());
            assertNull(request.getModelOverride());
            assertTrue(request.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("缺少 parentSessionKey 时抛 NPE")
        void shouldRejectMissingParentSessionKey() {
            assertThrows(NullPointerException.class, () ->
                    SpawnRequest.builder()
                            .task("task")
                            .build());
        }

        @Test
        @DisplayName("缺少 task 时抛 NPE")
        void shouldRejectMissingTask() {
            assertThrows(NullPointerException.class, () ->
                    SpawnRequest.builder()
                            .parentSessionKey("agent:w:main:s1")
                            .build());
        }

        @Test
        @DisplayName("可选字段正确填充")
        void shouldPopulateOptionalFields() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("task")
                    .agentId("specialist")
                    .modelOverride("claude-3-opus")
                    .metadata(Map.of("priority", "high"))
                    .build();

            assertEquals("specialist", request.getAgentId());
            assertEquals("claude-3-opus", request.getModelOverride());
            assertEquals("high", request.getMetadata().get("priority"));
        }

        @Test
        @DisplayName("metadata 防御性拷贝：修改原 Map 不影响 request")
        void metadataShouldBeDefensivelyCopied() {
            var mutable = new java.util.HashMap<String, Object>();
            mutable.put("key", "value");

            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("task")
                    .metadata(mutable)
                    .build();

            mutable.put("injected", "evil");

            assertFalse(request.getMetadata().containsKey("injected"));
            assertEquals(1, request.getMetadata().size());
        }

        @Test
        @DisplayName("metadata 返回不可变 Map")
        void metadataShouldBeUnmodifiable() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("task")
                    .metadata(Map.of("k", "v"))
                    .build();

            assertThrows(UnsupportedOperationException.class, () ->
                    request.getMetadata().put("injected", "evil"));
        }
    }

    @Nested
    @DisplayName("currentDepth 深度计算")
    class CurrentDepthTests {

        @Test
        @DisplayName("main session → depth 0")
        void mainSessionShouldBeDepthZero() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:session-123")
                    .task("task")
                    .build();

            assertEquals(0, request.currentDepth());
        }

        @Test
        @DisplayName("一层 subagent → depth 1")
        void oneSubagentShouldBeDepthOne() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1:subagent:abc123")
                    .task("task")
                    .build();

            assertEquals(1, request.currentDepth());
        }

        @Test
        @DisplayName("两层 subagent → depth 2")
        void twoSubagentsShouldBeDepthTwo() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:orch:main:s1:subagent:abc:subagent:def")
                    .task("task")
                    .build();

            assertEquals(2, request.currentDepth());
        }

        @Test
        @DisplayName("三层 subagent → depth 3")
        void threeSubagentsShouldBeDepthThree() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:a:main:s:subagent:1:subagent:2:subagent:3")
                    .task("task")
                    .build();

            assertEquals(3, request.currentDepth());
        }

        @Test
        @DisplayName("直接 subagent session (无 main 前缀) → depth 1")
        void directSubagentSessionShouldBeDepthOne() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:worker:subagent:uuid-here")
                    .task("task")
                    .build();

            assertEquals(1, request.currentDepth());
        }
    }
}
