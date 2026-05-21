package com.lightweightai.tools.client;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.ClientToolAliasRegistry;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GetPositionTool — client-side geolocation")
class GetPositionToolTest {

    @AfterEach
    void resetAliases() {
        ClientToolAliasRegistry.reset();
    }

    @Test
    @DisplayName("getName returns GetPosition")
    void nameIsGetPosition() {
        GetPositionTool tool = new GetPositionTool(noopDispatcher());
        assertEquals("GetPosition", tool.getName());
    }

    @Test
    @DisplayName("isAutoExecute is false for client tools")
    void notAutoExecute() {
        GetPositionTool tool = new GetPositionTool(noopDispatcher());
        assertFalse(tool.isAutoExecute());
    }

    @Test
    @DisplayName("execute returns success with parsed position data")
    void executeReturnsPosition() {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            Map<String, Object> position = Map.of(
                "longitude", 116.397128,
                "latitude", 39.916527,
                "locationSystem", "WGS84"
            );
            Map<String, Object> payload = Map.of(
                "errorCode", "0",
                "position", position,
                "city", "Beijing"
            );
            return CompletableFuture.completedFuture(
                ToolResult.success("ok", Map.of(
                    "header", Map.of("namespace", "GeoInformation", "name", "PositionInfo"),
                    "payload", payload
                ))
            );
        };

        GetPositionTool tool = new GetPositionTool(dispatcher);
        ToolResult result = tool.execute(Map.of("precision", 3, "needCityName", "true"));

        assertFalse(result.isError());
        assertTrue(result.hasStructuredContent());
        Map<String, Object> structured = result.getStructuredContent();
        assertEquals(116.397128, structured.get("longitude"));
        assertEquals(39.916527, structured.get("latitude"));
        assertEquals("Beijing", structured.get("city"));
    }

    @Test
    @DisplayName("execute returns error when errorCode is non-zero")
    void errorCodeNonZero() {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            Map<String, Object> payload = Map.of("errorCode", "1001");
            return CompletableFuture.completedFuture(
                ToolResult.success("fail", Map.of(
                    "header", Map.of("namespace", "GeoInformation", "name", "PositionInfo"),
                    "payload", payload
                ))
            );
        };

        GetPositionTool tool = new GetPositionTool(dispatcher);
        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError(), "non-zero errorCode must produce error result");
    }

    @Test
    @DisplayName("buildPayload includes precision and needCityName defaults")
    void buildPayloadDefaults() {
        Map<String, Object>[] capturedPayload = new Map[1];

        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            capturedPayload[0] = directive.getPayload();
            Map<String, Object> position = Map.of("longitude", 0, "latitude", 0, "locationSystem", "WGS84");
            return CompletableFuture.completedFuture(
                ToolResult.success("ok", Map.of(
                    "header", Map.of("namespace", "GeoInformation", "name", "PositionInfo"),
                    "payload", Map.of("errorCode", "0", "position", position, "city", "Test")
                ))
            );
        };

        GetPositionTool tool = new GetPositionTool(dispatcher);
        tool.execute(Map.of());

        assertNotNull(capturedPayload[0]);
        assertEquals(3, capturedPayload[0].get("precision"));
        assertEquals("true", capturedPayload[0].get("needCityName"));
    }

    @Test
    @DisplayName("directive descriptor has correct namespace and actions")
    void directiveDescriptor() {
        GetPositionTool tool = new GetPositionTool(noopDispatcher());

        assertEquals("GeoInformation", tool.getDirectiveDescriptor().getNamespace());
        assertEquals("GetPosition", tool.getDirectiveDescriptor().getDownAction());
        assertEquals("PositionInfo", tool.getDirectiveDescriptor().getUpAction());
        assertEquals(8_000, tool.getDirectiveDescriptor().getTimeoutMs());
    }

    private static ClientToolDispatcher noopDispatcher() {
        return (callId, toolName, directive) -> {
            Map<String, Object> position = Map.of("longitude", 0, "latitude", 0, "locationSystem", "WGS84");
            return CompletableFuture.completedFuture(
                ToolResult.success("ok", Map.of(
                    "header", Map.of("namespace", "GeoInformation", "name", "PositionInfo"),
                    "payload", Map.of("errorCode", "0", "position", position, "city", "Default")
                ))
            );
        };
    }
}
