package com.lightweightai.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("McpToolClient._meta 组装（progressToken + requestHeaders）")
class McpToolClientMetaTest {

    @Test
    @DisplayName("progressToken 与 requestMeta 同时写入 _meta")
    void buildsBothEntries() {
        Map<String, Object> meta = McpToolClient.buildCallMeta(
            "tok-1", Map.of("x-trace-id", "t-1", "x-uid", "u-1"));

        assertEquals("tok-1", meta.get("progressToken"));

        @SuppressWarnings("unchecked")
        Map<String, String> requestHeaders =
            (Map<String, String>) meta.get(McpRequestMetaContext.META_FIELD);
        assertEquals("t-1", requestHeaders.get("x-trace-id"));
        assertEquals("u-1", requestHeaders.get("x-uid"));
    }

    @Test
    @DisplayName("requestMeta 为空时不写入 requestHeaders 字段")
    void omitsRequestHeadersWhenEmpty() {
        Map<String, Object> meta = McpToolClient.buildCallMeta("tok-1", Map.of());
        assertEquals("tok-1", meta.get("progressToken"));
        assertFalse(meta.containsKey(McpRequestMetaContext.META_FIELD));
    }

    @Test
    @DisplayName("progressToken 为 null 时仅保留 requestHeaders")
    void onlyRequestHeadersWhenNoProgressToken() {
        Map<String, Object> meta = McpToolClient.buildCallMeta(null, Map.of("k", "v"));
        assertNull(meta.get("progressToken"));
        assertTrue(meta.containsKey(McpRequestMetaContext.META_FIELD));
    }

    @Test
    @DisplayName("mergeRequestMeta: per-call（Reactor Context）覆盖实例级 setRequestMeta 同名 key")
    void perCallOverridesInstanceDefault() {
        Map<String, String> base = Map.of("x-uid", "u-default", "x-app", "app-1");
        Map<String, String> perCall = Map.of("x-uid", "u-call", "x-trace", "t-1");

        Map<String, String> merged = McpToolClient.mergeRequestMeta(base, perCall);

        assertEquals("u-call", merged.get("x-uid"));   // per-call 覆盖
        assertEquals("app-1", merged.get("x-app"));     // 实例级保留
        assertEquals("t-1", merged.get("x-trace"));     // per-call 新增
    }

    @Test
    @DisplayName("mergeRequestMeta: 任一为空时退化为另一方，全空返回空 map")
    void mergeHandlesEmpties() {
        assertEquals(Map.of("a", "1"), McpToolClient.mergeRequestMeta(Map.of("a", "1"), Map.of()));
        assertEquals(Map.of("b", "2"), McpToolClient.mergeRequestMeta(null, Map.of("b", "2")));
        assertTrue(McpToolClient.mergeRequestMeta(Map.of(), null).isEmpty());
    }
}
