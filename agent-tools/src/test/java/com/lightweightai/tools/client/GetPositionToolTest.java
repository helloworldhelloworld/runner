package com.lightweightai.tools.client;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GetPositionTool — client-side geo positioning")
class GetPositionToolTest {

    @Test
    @DisplayName("tool name is 'GetPosition' from annotation")
    void toolNameFromAnnotation() {
        GetPositionTool tool = new GetPositionTool(noOpDispatcher());
        assertEquals("GetPosition", tool.getName());
    }

    @Test
    @DisplayName("buildPayload includes precision and needCityName defaults")
    void buildPayloadDefaults() {
        GetPositionTool tool = new GetPositionTool(noOpDispatcher());
        Map<String, Object> payload = tool.buildPayload(Map.of());

        assertEquals(3, payload.get("precision"));
        assertEquals("true", payload.get("needCityName"));
    }

    @Test
    @DisplayName("buildPayload uses provided precision and needCityName")
    void buildPayloadCustomValues() {
        GetPositionTool tool = new GetPositionTool(noOpDispatcher());
        Map<String, Object> payload = tool.buildPayload(Map.of(
                "precision", 5,
                "needCityName", "false"
        ));

        assertEquals(5, payload.get("precision"));
        assertEquals("false", payload.get("needCityName"));
    }

    @Test
    @DisplayName("parseResult extracts position fields correctly")
    void parseResultExtractsFields() {
        GetPositionTool tool = new GetPositionTool(noOpDispatcher());

        Map<String, Object> position = Map.of(
                "longitude", "121.4737",
                "latitude", "31.2304",
                "locationSystem", "WGS84"
        );
        Map<String, Object> structuredContent = Map.of(
                "errorCode", "0",
                "position", position,
                "city", "Shanghai"
        );

        ToolResult input = ToolResult.success("ok", structuredContent);
        Map<String, Object> parsed = tool.parseResult(input);

        assertEquals("121.4737", parsed.get("longitude"));
        assertEquals("31.2304", parsed.get("latitude"));
        assertEquals("WGS84", parsed.get("locationSystem"));
        assertEquals("Shanghai", parsed.get("city"));
    }

    @Test
    @DisplayName("parseResult throws on error result")
    void parseResultThrowsOnError() {
        GetPositionTool tool = new GetPositionTool(noOpDispatcher());
        ToolResult errorResult = ToolResult.error("GPS unavailable");

        assertThrows(IllegalStateException.class, () -> tool.parseResult(errorResult));
    }

    @Test
    @DisplayName("parseResult throws on non-zero errorCode")
    void parseResultThrowsOnNonZeroErrorCode() {
        GetPositionTool tool = new GetPositionTool(noOpDispatcher());
        Map<String, Object> structuredContent = Map.of(
                "errorCode", "1001",
                "position", Map.of()
        );
        ToolResult input = ToolResult.success("ok", structuredContent);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.parseResult(input));
        assertTrue(ex.getMessage().contains("1001"));
    }

    @Test
    @DisplayName("parseResult throws on null payload")
    void parseResultThrowsOnNullPayload() {
        GetPositionTool tool = new GetPositionTool(noOpDispatcher());
        ToolResult input = ToolResult.success("ok");

        assertThrows(IllegalStateException.class, () -> tool.parseResult(input));
    }

    @Test
    @DisplayName("null dispatcher throws NullPointerException")
    void nullDispatcherThrows() {
        assertThrows(NullPointerException.class, () -> new GetPositionTool(null));
    }

    private ClientToolDispatcher noOpDispatcher() {
        return (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok"));
    }
}
