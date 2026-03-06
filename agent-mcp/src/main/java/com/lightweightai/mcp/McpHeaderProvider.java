package com.lightweightai.mcp;

import java.util.Map;

/**
 * MCP HTTP Header 动态提供者
 *
 * 在每次 MCP 请求发送前调用，返回需要附加到 HTTP 请求的自定义 Header。
 * 适用于 SSE 和 Streamable HTTP 传输（STDIO 忽略）。
 *
 * <pre>
 * // 动态 Bearer Token
 * McpHeaderProvider provider = () -> Map.of(
 *     "Authorization", "Bearer " + tokenService.getToken(),
 *     "X-Trace-Id", TraceContext.traceId()
 * );
 *
 * ToolClient client = ToolClient.create()
 *     .headerProvider(provider)
 *     .fromConfig(config)
 *     .build();
 * </pre>
 */
@FunctionalInterface
public interface McpHeaderProvider {

    /**
     * 返回需要附加到 MCP HTTP 请求的 Header
     *
     * @return header name → value 映射，不为 null
     */
    Map<String, String> getHeaders();
}
