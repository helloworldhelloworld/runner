package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ClientTool;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 端侧定位工具（GeoInformation.GetPosition → GeoInformation.PositionInfo）。
 */
@ClientTool(
    toolName = "GetPosition",
    downAction = "GetPosition",
    upAction = "PositionInfo",
    namespace = "GeoInformation",
    timeoutMs = 8_000,
    version = "1.1"
)
public class GetPositionTool extends ClientTool<Map<String, Object>> {

    public GetPositionTool(ClientToolDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    protected Map<String, Object> buildPayload(Map<String, Object> args) {
        return Map.of(
            "precision", args.getOrDefault("precision", 3),
            "needCityName", args.getOrDefault("needCityName", "true")
        );
    }

    @Override
    protected Map<String, Object> parseResult(ToolResult result) {
        if (result.isError()) {
            throw new IllegalStateException(result.getContent());
        }

        Map<String, Object> payload = result.getStructuredContent();
        if (payload == null) {
            throw new IllegalStateException("GetPosition payload is empty");
        }

        Object errorCode = payload.get("errorCode");
        if (errorCode != null && !"0".equals(String.valueOf(errorCode))) {
            throw new IllegalStateException("GetPosition failed, errorCode=" + errorCode);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> position = (Map<String, Object>) payload.getOrDefault("position", Map.of());
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("longitude", position.get("longitude"));
        parsed.put("latitude", position.get("latitude"));
        parsed.put("locationSystem", position.get("locationSystem"));
        parsed.put("city", payload.get("city"));
        return parsed;
    }
}
