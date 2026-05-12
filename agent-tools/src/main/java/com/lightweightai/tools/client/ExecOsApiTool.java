package com.lightweightai.tools.client;

import com.lightweightai.kernel.agent.ClientTool;
import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 端侧 OS API 执行工具（FunctionExecute.ExecuteOSAPI → FunctionExecute.ExecuteOSAPIRsp）。
 *
 * <p>同一组上下行指令可在 LLM 端以多个工具名暴露（通过 {@code aliases} 增量配置），
 * 无需新增工具类。当端侧/产品方需要追加别名时，仅修改注解即可。</p>
 */
@com.lightweightai.kernel.agent.annotation.ClientTool(
    toolName = "ExecOsApi",
    aliases = {"ExecuteOSAPI", "OsApiInvoke"},
    downAction = "ExecuteOSAPI",
    upAction = "ExecuteOSAPIRsp",
    namespace = "FunctionExecute",
    timeoutMs = 30_000,
    version = "1.1"
)
public class ExecOsApiTool extends ClientTool<Map<String, Object>> {

    private static final String API_NAME_KEY = "apiName";
    private static final String PARAMS_KEY = "params";

    public ExecOsApiTool(ClientToolDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    protected Map<String, Object> buildPayload(Map<String, Object> args) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Object apiName = args.get(API_NAME_KEY);
        if (apiName == null || apiName.toString().isBlank()) {
            throw new IllegalArgumentException("ExecOsApi requires 'apiName'");
        }
        payload.put(API_NAME_KEY, apiName);
        Object params = args.get(PARAMS_KEY);
        if (params instanceof Map<?, ?> map) {
            payload.put(PARAMS_KEY, map);
        } else if (params != null) {
            payload.put(PARAMS_KEY, params);
        } else {
            payload.put(PARAMS_KEY, Map.of());
        }
        return payload;
    }

    @Override
    protected Map<String, Object> parseResult(ToolResult result) {
        if (result.isError()) {
            throw new IllegalStateException(result.getContent());
        }

        Map<String, Object> payload = result.getStructuredContent();
        if (payload == null) {
            throw new IllegalStateException("ExecOsApi payload is empty");
        }

        Object errorCode = payload.get("errorCode");
        if (errorCode != null && !"0".equals(String.valueOf(errorCode))) {
            throw new IllegalStateException(
                "ExecOsApi failed, errorCode=" + errorCode
                    + (payload.get("errorMsg") != null ? ", errorMsg=" + payload.get("errorMsg") : ""));
        }

        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("errorCode", errorCode);
        if (payload.containsKey("errorMsg")) {
            parsed.put("errorMsg", payload.get("errorMsg"));
        }
        if (payload.containsKey("data")) {
            parsed.put("data", payload.get("data"));
        }
        return parsed;
    }
}
