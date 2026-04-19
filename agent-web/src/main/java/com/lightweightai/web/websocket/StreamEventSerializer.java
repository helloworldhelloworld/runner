package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.util.TextTruncator;

/**
 * StreamEvent → WebSocket JSON 序列化
 *
 * 提取自 VertxChatWebSocketHandler 和 SpringChatWebSocketHandler 的共性逻辑。
 * 两个 handler 都委托给此类，消除重复。
 */
public class StreamEventSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将 StreamEvent 序列化为 WebSocket JSON 字符串。
     * 返回 null 表示此事件不需要发送给客户端（如 TOOL_RESULT、LLM_COMPLETE）。
     */
    public static String serialize(StreamEvent event) {
        try {
            return switch (event.getType()) {
                case TEXT_DELTA -> tokenJson(event.getTextDelta());
                case TOOL_CALL_START -> toolCallStartJson(event);
                case TOOL_PROGRESS -> toolProgressJson(event);
                case TOOL_LOG -> toolLogJson(event);
                case TOOL_RESULT -> toolResultJson(event);
                case TOOL_ERROR -> toolErrorJson(event);
                case POST_PROCESS_DATA -> postProcessJson(event);
                case TRACE -> traceJson(event);
                case ERROR -> errorJson(event.getError() != null ? event.getError().getMessage() : "Unknown error");

                // Orchestrator + Subagent 事件
                case AGENT_ROUTE, AGENT_INTERRUPT, AGENT_RESUME,
                     SUBAGENT_SPAWN, SUBAGENT_COMPLETE, SUBAGENT_ERROR, SUBAGENT_CANCELLED ->
                        eventWithDataJson(eventTypeName(event.getType()), event);

                // 不推送给客户端的事件（LLM_COMPLETE 是内部信号）
                case LLM_COMPLETE -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    public static String tokenJson(String delta) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "token");
        msg.put("data", delta);
        return MAPPER.writeValueAsString(msg);
    }

    public static String streamEndJson(String emotion) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "stream_end");
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("emotion", emotion != null ? emotion : "");
        msg.set("meta", meta);
        return MAPPER.writeValueAsString(msg);
    }

    public static String errorJson(String message) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "error");
        msg.put("message", message);
        return MAPPER.writeValueAsString(msg);
    }

    private static String toolCallStartJson(StreamEvent event) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "tool_call_start");
        ObjectNode data = MAPPER.createObjectNode();
        ToolCall tc = event.getToolCall();
        if (tc != null) {
            data.put("toolCallId", tc.getId());
            data.put("toolName", tc.getName());
            if (tc.getArguments() != null) {
                data.set("arguments", MAPPER.valueToTree(tc.getArguments()));
            }
        }
        msg.set("data", data);
        return MAPPER.writeValueAsString(msg);
    }

    /** WS 载荷里工具结果内容的上限 —— 大内容用截断标记替代，防帧爆炸。 */
    private static final int TOOL_RESULT_CONTENT_LIMIT = 4096;

    private static String toolResultJson(StreamEvent event) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "tool_result");
        ObjectNode data = MAPPER.createObjectNode();
        ToolResultChunk chunk = event.getChunk();
        if (chunk != null) {
            data.put("toolName", chunk.getToolName());
            data.put("toolCallId", chunk.getToolCallId());
            ToolResult result = chunk.getResult();
            ObjectNode resultNode = MAPPER.createObjectNode();
            if (result != null) {
                resultNode.put("content",
                        TextTruncator.truncate(result.getContent(), TOOL_RESULT_CONTENT_LIMIT));
                resultNode.put("isError", result.isError());
            } else {
                // 理论上不会发生 —— COMPLETE 分支必有 result —— 防御性处理
                resultNode.put("content", "");
                resultNode.put("isError", false);
            }
            data.set("result", resultNode);
        }
        msg.set("data", data);
        return MAPPER.writeValueAsString(msg);
    }

    private static String toolProgressJson(StreamEvent event) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "tool_progress");
        ObjectNode data = MAPPER.createObjectNode();
        ToolResultChunk chunk = event.getChunk();
        if (chunk != null) {
            data.put("toolName", chunk.getToolName());
            data.put("message", chunk.getMessage());
            data.put("progress", chunk.getProgress());
            data.put("total", chunk.getTotal());
        }
        msg.set("data", data);
        return MAPPER.writeValueAsString(msg);
    }

    private static String toolLogJson(StreamEvent event) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "tool_log");
        ObjectNode data = MAPPER.createObjectNode();
        ToolResultChunk chunk = event.getChunk();
        if (chunk != null) {
            data.put("toolName", chunk.getToolName());
            data.put("message", chunk.getMessage());
            if (chunk.getMeta() != null) {
                data.set("meta", MAPPER.valueToTree(chunk.getMeta()));
            }
        }
        msg.set("data", data);
        return MAPPER.writeValueAsString(msg);
    }

    private static String toolErrorJson(StreamEvent event) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "tool_error");
        ObjectNode data = MAPPER.createObjectNode();
        ToolResultChunk chunk = event.getChunk();
        if (chunk != null) {
            data.put("toolName", chunk.getToolName());
            data.put("message", chunk.getMessage());
        }
        msg.set("data", data);
        return MAPPER.writeValueAsString(msg);
    }

    private static String postProcessJson(StreamEvent event) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "post_process");
        msg.put("category", event.getCategory() != null ? event.getCategory() : "");
        if (event.getData() != null) {
            msg.set("data", MAPPER.valueToTree(event.getData()));
        }
        return MAPPER.writeValueAsString(msg);
    }

    private static String traceJson(StreamEvent event) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "trace");
        ObjectNode data = MAPPER.createObjectNode();
        data.put("phase", event.getTracePhase());
        data.put("message", event.getTraceMessage());
        data.put("timestamp", event.getTraceTimestamp());
        if (event.getData() != null) {
            data.set("extra", MAPPER.valueToTree(event.getData()));
        }
        msg.set("data", data);
        return MAPPER.writeValueAsString(msg);
    }

    private static String eventWithDataJson(String type, StreamEvent event) throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", type);
        if (event.getData() != null) {
            msg.set("data", MAPPER.valueToTree(event.getData()));
        }
        if (event.getTraceMessage() != null) {
            msg.put("message", event.getTraceMessage());
        }
        msg.put("timestamp", event.getTraceTimestamp());
        return MAPPER.writeValueAsString(msg);
    }

    private static String eventTypeName(StreamEvent.EventType type) {
        return switch (type) {
            case AGENT_ROUTE -> "agent_route";
            case AGENT_INTERRUPT -> "agent_interrupt";
            case AGENT_RESUME -> "agent_resume";
            case SUBAGENT_SPAWN -> "subagent_spawn";
            case SUBAGENT_COMPLETE -> "subagent_complete";
            case SUBAGENT_ERROR -> "subagent_error";
            case SUBAGENT_CANCELLED -> "subagent_cancelled";
            default -> type.name().toLowerCase();
        };
    }
}
