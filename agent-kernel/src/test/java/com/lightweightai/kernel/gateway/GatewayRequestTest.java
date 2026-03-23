package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GatewayRequest - 协议无关请求模型")
class GatewayRequestTest {

    @Test
    @DisplayName("构建基本请求")
    void buildBasicRequest() {
        GatewayRequest req = GatewayRequest.builder()
            .message("hello")
            .build();

        assertEquals("hello", req.getMessage());
        assertNotNull(req.getRequestId());
        assertEquals("default", req.getSessionId());
        assertTrue(req.getTimestamp() > 0);
        assertTrue(req.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("自定义 requestId 和 sessionId")
    void customIds() {
        GatewayRequest req = GatewayRequest.builder()
            .requestId("req-1")
            .sessionId("session-1")
            .message("test")
            .build();

        assertEquals("req-1", req.getRequestId());
        assertEquals("session-1", req.getSessionId());
    }

    @Test
    @DisplayName("缺少 message 抛 NullPointerException")
    void missingMessageThrows() {
        assertThrows(NullPointerException.class, () ->
            GatewayRequest.builder().build()
        );
    }

    @Test
    @DisplayName("添加单个 metadata")
    void addSingleMetadata() {
        GatewayRequest req = GatewayRequest.builder()
            .message("test")
            .metadata("key", "value")
            .build();

        assertEquals("value", req.getMetadata().get("key"));
    }

    @Test
    @DisplayName("批量添加 metadata")
    void addBulkMetadata() {
        GatewayRequest req = GatewayRequest.builder()
            .message("test")
            .metadata(Map.of("a", 1, "b", 2))
            .build();

        assertEquals(1, req.getMetadata().get("a"));
        assertEquals(2, req.getMetadata().get("b"));
    }

    @Test
    @DisplayName("getMetadata 返回防御性拷贝")
    void metadataDefensiveCopy() {
        GatewayRequest req = GatewayRequest.builder()
            .message("test")
            .metadata("k", "v")
            .build();

        Map<String, Object> meta = req.getMetadata();
        meta.clear();
        assertEquals(1, req.getMetadata().size());
    }

    @Test
    @DisplayName("toString 输出 - 短消息")
    void toStringShort() {
        GatewayRequest req = GatewayRequest.builder()
            .message("hello")
            .build();

        String str = req.toString();
        assertTrue(str.contains("GatewayRequest"));
        assertTrue(str.contains("hello"));
    }

    @Test
    @DisplayName("toString 输出 - 长消息截断")
    void toStringLongMessage() {
        String longMsg = "a".repeat(100);
        GatewayRequest req = GatewayRequest.builder()
            .message(longMsg)
            .build();

        String str = req.toString();
        assertTrue(str.contains("..."));
    }
}
