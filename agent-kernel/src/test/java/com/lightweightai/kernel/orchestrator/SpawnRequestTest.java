package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - Subagent spawn 请求参数")
class SpawnRequestTest {

    @Nested
    @DisplayName("currentDepth - 从 session key 解析嵌套深度")
    class CurrentDepthTests {

        @Test
        @DisplayName("main session key → depth 0")
        void mainSessionKeyDepthZero() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:sess-1")
                    .task("test")
                    .build();

            assertEquals(0, request.currentDepth());
        }

        @Test
        @DisplayName("一层 subagent → depth 1")
        void singleSubagentDepthOne() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:sess-1:subagent:abc123")
                    .task("test")
                    .build();

            assertEquals(1, request.currentDepth());
        }

        @Test
        @DisplayName("两层嵌套 subagent → depth 2")
        void nestedSubagentDepthTwo() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:sess-1:subagent:abc123:subagent:def456")
                    .task("test")
                    .build();

            assertEquals(2, request.currentDepth());
        }

        @Test
        @DisplayName("三层嵌套 subagent → depth 3")
        void tripleNestedDepthThree() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1:subagent:a:subagent:b:subagent:c")
                    .task("test")
                    .build();

            assertEquals(3, request.currentDepth());
        }
    }

    @Nested
    @DisplayName("Builder 验证")
    class BuilderValidationTests {

        @Test
        @DisplayName("parentSessionKey 为 null 时抛出 NullPointerException")
        void nullParentSessionKeyThrows() {
            assertThrows(NullPointerException.class, () ->
                    SpawnRequest.builder()
                            .task("test")
                            .build());
        }

        @Test
        @DisplayName("task 为 null 时抛出 NullPointerException")
        void nullTaskThrows() {
            assertThrows(NullPointerException.class, () ->
                    SpawnRequest.builder()
                            .parentSessionKey("agent:w:main:s1")
                            .build());
        }

        @Test
        @DisplayName("所有必填字段设置后正常构建")
        void buildsWithRequiredFields() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("搜索文档")
                    .build();

            assertEquals("agent:w:main:s1", request.getParentSessionKey());
            assertEquals("搜索文档", request.getTask());
            assertNull(request.getAgentId());
            assertNull(request.getModelOverride());
            assertEquals(Map.of(), request.getMetadata());
        }

        @Test
        @DisplayName("可选字段正确设置")
        void buildsWithOptionalFields() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("搜索")
                    .agentId("researcher")
                    .modelOverride("claude-3-opus")
                    .metadata(Map.of("priority", "high"))
                    .build();

            assertEquals("researcher", request.getAgentId());
            assertEquals("claude-3-opus", request.getModelOverride());
            assertEquals("high", request.getMetadata().get("priority"));
        }

        @Test
        @DisplayName("metadata 防御性复制 — 外部修改不影响内部")
        void metadataDefensiveCopy() {
            Map<String, Object> original = new java.util.HashMap<>();
            original.put("key", "value");

            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("test")
                    .metadata(original)
                    .build();

            original.put("key2", "value2");
            assertFalse(request.getMetadata().containsKey("key2"),
                    "Mutation of original map should not affect SpawnRequest");
        }

        @Test
        @DisplayName("metadata 不可变 — 返回的 Map 不可修改")
        void metadataIsUnmodifiable() {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("test")
                    .metadata(Map.of("k", "v"))
                    .build();

            assertThrows(UnsupportedOperationException.class, () ->
                    request.getMetadata().put("new", "value"));
        }
    }
}
