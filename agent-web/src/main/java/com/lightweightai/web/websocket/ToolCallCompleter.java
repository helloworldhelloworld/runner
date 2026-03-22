package com.lightweightai.web.websocket;

import com.lightweightai.kernel.llm.ToolResult;

/**
 * 端工具调用完成接口 — 由 ClientToolResultRouter 用于解耦具体 dispatcher 实现。
 */
public interface ToolCallCompleter {

    boolean completeCall(String callId, ToolResult result);

    boolean completeLatestCall(ToolResult result);
}
