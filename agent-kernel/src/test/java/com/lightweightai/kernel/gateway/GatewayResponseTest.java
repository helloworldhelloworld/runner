package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GatewayResponse - 协议无关响应模型")
class GatewayResponseTest {

    @Test
    @DisplayName("构建基本响应")
    void buildBasicResponse() {
        GatewayResponse resp = GatewayResponse.builder()
            .requestId("req-1")
            .sessionId("s-1")
            .text("Hello")
            .latencyMs(42)
            .build();

        assertEquals("req-1", resp.getRequestId());
        assertEquals("s-1", resp.getSessionId());
        assertEquals("Hello", resp.getText());
        assertEquals(42, resp.getLatencyMs());
        assertFalse(resp.isError());
        assertTrue(resp.getTimestamp() > 0);
    }

    @Test
    @DisplayName("默认值正确")
    void defaultValues() {
        GatewayResponse resp = GatewayResponse.builder().build();

        assertEquals("", resp.getText());
        assertFalse(resp.isError());
        assertEquals(0, resp.getLatencyMs());
        assertTrue(resp.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("创建错误响应")
    void errorResponse() {
        GatewayResponse resp = GatewayResponse.error("req-1", "s-1", "boom");

        assertTrue(resp.isError());
        assertEquals("boom", resp.getErrorMessage());
        assertEquals("req-1", resp.getRequestId());
        assertEquals("s-1", resp.getSessionId());
    }

    @Test
    @DisplayName("从 AgentResponse 创建")
    void fromAgentResponse() {
        AgentResponse agent = AgentResponse.builder()
            .text("response text")
            .stopReason("end_turn")
            .toolCalls(List.of())
            .build();

        GatewayResponse resp = GatewayResponse.fromAgentResponse(agent, "req-1", "s-1", 100);

        assertEquals("response text", resp.getText());
        assertEquals("req-1", resp.getRequestId());
        assertEquals("s-1", resp.getSessionId());
        assertEquals(100, resp.getLatencyMs());
        assertEquals("end_turn", resp.getMetadata().get("stopReason"));
        assertEquals(0, resp.getMetadata().get("toolCallCount"));
    }

    @Test
    @DisplayName("metadata 操作")
    void metadataOperations() {
        GatewayResponse resp = GatewayResponse.builder()
            .metadata("key1", "val1")
            .metadata(Map.of("key2", "val2"))
            .build();

        Map<String, Object> meta = resp.getMetadata();
        assertEquals("val1", meta.get("key1"));
        assertEquals("val2", meta.get("key2"));
    }

    @Test
    @DisplayName("metadata null map 不报错")
    void metadataNullMap() {
        assertDoesNotThrow(() ->
            GatewayResponse.builder()
                .metadata((Map<String, Object>) null)
                .build()
        );
    }

    @Test
    @DisplayName("getMetadata 返回防御性拷贝")
    void metadataDefensiveCopy() {
        GatewayResponse resp = GatewayResponse.builder()
            .metadata("k", "v")
            .build();

        resp.getMetadata().clear();
        assertEquals(1, resp.getMetadata().size());
    }

    @Test
    @DisplayName("toString 成功响应")
    void toStringSuccess() {
        GatewayResponse resp = GatewayResponse.builder()
            .requestId("req-1")
            .text("hello")
            .latencyMs(10)
            .build();

        String str = resp.toString();
        assertTrue(str.contains("req-1"));
        assertTrue(str.contains("hello"));
        assertTrue(str.contains("10ms"));
    }

    @Test
    @DisplayName("toString 错误响应")
    void toStringError() {
        GatewayResponse resp = GatewayResponse.error("req-1", "s-1", "failure");

        String str = resp.toString();
        assertTrue(str.contains("error=true"));
        assertTrue(str.contains("failure"));
    }

    @Test
    @DisplayName("toString 长文本截断")
    void toStringLongText() {
        GatewayResponse resp = GatewayResponse.builder()
            .text("x".repeat(100))
            .build();

        assertTrue(resp.toString().contains("..."));
    }
}
