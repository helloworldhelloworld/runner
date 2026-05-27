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
}
