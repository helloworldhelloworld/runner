package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.DeviceContext;
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

    // ==================== DeviceContext ====================

    @Test
    @DisplayName("通过 builder 设置 DeviceContext")
    void shouldSetDeviceContext() {
        DeviceContext ctx = DeviceContext.of("phone", "2.0");
        GatewayRequest req = GatewayRequest.builder()
            .message("test")
            .deviceContext(ctx)
            .build();

        assertEquals(ctx, req.getDeviceContext());
        assertEquals("phone", req.getDeviceContext().getDeviceType());
    }

    @Test
    @DisplayName("未设置 DeviceContext 时返回 DEFAULT")
    void shouldReturnDefaultDeviceContext() {
        GatewayRequest req = GatewayRequest.builder()
            .message("test")
            .build();

        assertEquals(DeviceContext.DEFAULT, req.getDeviceContext());
    }

    @Test
    @DisplayName("metadata 中非 DeviceContext 类型返回 DEFAULT")
    void shouldReturnDefaultForWrongDeviceContextType() {
        GatewayRequest req = GatewayRequest.builder()
            .message("test")
            .metadata("deviceContext", "not-a-context")
            .build();

        assertEquals(DeviceContext.DEFAULT, req.getDeviceContext());
    }

    // ==================== Unique IDs ====================

    @Test
    @DisplayName("两次构建产生不同的自动 requestId")
    void shouldGenerateUniqueRequestIds() {
        GatewayRequest r1 = GatewayRequest.builder().message("a").build();
        GatewayRequest r2 = GatewayRequest.builder().message("b").build();

        assertNotEquals(r1.getRequestId(), r2.getRequestId());
    }

    @Test
    @DisplayName("timestamp 在构建前后范围内")
    void shouldHaveTimestampInRange() {
        long before = System.currentTimeMillis();
        GatewayRequest req = GatewayRequest.builder().message("test").build();
        long after = System.currentTimeMillis();

        assertTrue(req.getTimestamp() >= before);
        assertTrue(req.getTimestamp() <= after);
    }
}
