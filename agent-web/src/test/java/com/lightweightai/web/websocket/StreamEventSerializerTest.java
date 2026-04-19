package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test：WebSocket 序列化对 TOOL_RESULT 的处理。
 *
 * 修复前的红灯：line 41 的 `case TOOL_RESULT -> null` 让 tool_result 帧永远发不出去。
 * 修复后：TOOL_RESULT 帧应含 toolName、toolCallId、result.content、result.isError。
 */
@DisplayName("StreamEventSerializer - TOOL_RESULT 放行 + 截断")
class StreamEventSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TOOL_RESULT 序列化为 type=tool_result，data 里含 toolName/toolCallId/result")
    void toolResultIsSerialized() throws Exception {
        ToolResult result = ToolResult.success("call-42", "It's sunny.");
        ToolResultChunk chunk = ToolResultChunk.complete("get_weather", result)
                .withToolCallId("call-42");
        StreamEvent event = StreamEvent.toolResult(chunk);

        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json, "TOOL_RESULT 不应再返回 null —— 这是修复的核心");
        JsonNode node = MAPPER.readTree(json);
        assertEquals("tool_result", node.get("type").asText());
        JsonNode data = node.get("data");
        assertNotNull(data);
        assertEquals("get_weather", data.get("toolName").asText());
        assertEquals("call-42", data.get("toolCallId").asText());
        JsonNode resultNode = data.get("result");
        assertNotNull(resultNode, "data.result 必须存在");
        assertTrue(resultNode.get("content").asText().contains("sunny"),
                "data.result.content 应含工具输出");
        assertFalse(resultNode.get("isError").asBoolean(), "非错误结果 isError=false");
    }

    @Test
    @DisplayName("超长 content 被截断并带剩余字符数标记（防 WS 帧爆炸）")
    void oversizedContentIsTruncated() throws Exception {
        String huge = "x".repeat(10_000); // 远大于 4 KB 阈值
        ToolResult result = ToolResult.success("call-big", huge);
        ToolResultChunk chunk = ToolResultChunk.complete("dump", result)
                .withToolCallId("call-big");

        String json = StreamEventSerializer.serialize(StreamEvent.toolResult(chunk));

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        String content = node.get("data").get("result").get("content").asText();
        assertTrue(content.length() < huge.length(), "超长内容应被截断: " + content.length());
        assertTrue(content.contains("+") && content.contains("chars"),
                "应带省略标记，实际: " + content.substring(Math.max(0, content.length() - 40)));
    }

    @Test
    @DisplayName("LLM_COMPLETE 仍返回 null（非面向用户事件）")
    void llmCompleteStillSuppressed() {
        StreamEvent event = StreamEvent.llmComplete(
                com.lightweightai.kernel.llm.LLMResponse.builder().stopReason("end_turn").build());
        assertNull(StreamEventSerializer.serialize(event),
                "LLM_COMPLETE 是内部信号，不应下发到客户端");
    }

    @Test
    @DisplayName("错误型 TOOL_RESULT 保留 isError=true 且带 message")
    void errorToolResultCarriesErrorFlag() throws Exception {
        ToolResult errResult = ToolResult.error("call-err", "boom");
        ToolResultChunk chunk = ToolResultChunk.complete("bad_tool", errResult)
                .withToolCallId("call-err");

        String json = StreamEventSerializer.serialize(StreamEvent.toolResult(chunk));

        JsonNode data = MAPPER.readTree(json).get("data");
        JsonNode resultNode = data.get("result");
        assertTrue(resultNode.get("isError").asBoolean(), "错误型 result isError 应为 true");
        assertTrue(resultNode.get("content").asText().contains("boom"));
    }
}
