package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ClientToolResultRouter 完整覆盖测试。
 *
 * 已有 ClientToolResultRouterTest 覆盖：缺 callId 拒绝、directiveResult 基本路由、缺 directiveId 拒绝。
 * 此测试补充：
 * - 成功的 client_tool_result 路由 + callId 正确传递
 * - error 标记正确传导
 * - directive_result 的 JSON 结构化内容解析
 * - directive_result 非 JSON content 的普通字符串处理
 * - directive_result failure（success=false）
 * - NullPointerException 防御（dispatcher/payload 为 null）
 */
@DisplayName("ClientToolResultRouter - 完整覆盖")
class ClientToolResultRouterFullTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("routeClientToolResult")
    class RouteClientToolResult {

        @Test
        @DisplayName("成功路由：callId + content 正确传递到 dispatcher")
        void successRoutePassesCallIdAndContent() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            when(dispatcher.completeCall(eq("call-42"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "call-42");
            payload.put("content", "It's sunny in Shanghai.");
            payload.put("isError", false);

            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertTrue(router.routeClientToolResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("call-42"), captor.capture());
            ToolResult result = captor.getValue();
            assertEquals("It's sunny in Shanghai.", result.getContent());
            assertFalse(result.isError());
        }

        @Test
        @DisplayName("错误结果路由：isError=true 时 ToolResult.isError 为 true")
        void errorFlagPropagatesToToolResult() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            when(dispatcher.completeCall(eq("call-err"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "call-err");
            payload.put("content", "timeout");
            payload.put("isError", true);

            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertTrue(router.routeClientToolResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("call-err"), captor.capture());
            assertTrue(captor.getValue().isError());
            assertEquals("timeout", captor.getValue().getContent());
        }

        @Test
        @DisplayName("content 缺失时默认为空字符串")
        void missingContentDefaultsToEmpty() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            when(dispatcher.completeCall(any(), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "call-x");

            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertTrue(router.routeClientToolResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("call-x"), captor.capture());
            assertEquals("", captor.getValue().getContent());
        }
    }

    @Nested
    @DisplayName("routeDirectiveResult")
    class RouteDirectiveResult {

        @Test
        @DisplayName("success=false 时创建 error ToolResult")
        void failureCreatesErrorResult() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            when(dispatcher.completeCall(eq("d-fail"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-fail");
            payload.put("success", false);
            payload.put("content", "device not found");

            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-fail"), captor.capture());
            assertTrue(captor.getValue().isError());
            assertEquals("device not found", captor.getValue().getContent());
        }

        @Test
        @DisplayName("JSON 内容被解析为 structuredContent")
        void jsonContentParsedAsStructured() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            when(dispatcher.completeCall(eq("d-json"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-json");
            payload.put("success", true);
            payload.put("content", "{\"header\":\"h1\",\"payload\":\"data\"}");

            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-json"), captor.capture());
            ToolResult result = captor.getValue();
            assertFalse(result.isError());
            assertTrue(result.hasStructuredContent(),
                    "JSON content should be parsed as structuredContent");
            assertEquals("h1", result.getStructuredContent().get("header"));
        }

        @Test
        @DisplayName("非 JSON 纯文本 content 不带 structuredContent")
        void plainTextContentHasNoStructured() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            when(dispatcher.completeCall(eq("d-plain"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-plain");
            payload.put("success", true);
            payload.put("content", "just plain text");

            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-plain"), captor.capture());
            assertFalse(captor.getValue().hasStructuredContent(),
                    "Plain text should not have structuredContent");
        }

        @Test
        @DisplayName("空 content 不带 structuredContent")
        void emptyContentHasNoStructured() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            when(dispatcher.completeCall(eq("d-empty"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-empty");
            payload.put("success", true);
            payload.put("content", "");

            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-empty"), captor.capture());
            assertFalse(captor.getValue().hasStructuredContent());
        }

        @Test
        @DisplayName("不合法 JSON content（以 { 开头但无法解析）退回纯文本")
        void malformedJsonFallsBackToPlainText() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            when(dispatcher.completeCall(eq("d-bad"), any(ToolResult.class))).thenReturn(true);

            ObjectNode payload = mapper.createObjectNode();
            payload.put("directiveId", "d-bad");
            payload.put("success", true);
            payload.put("content", "{invalid json here");

            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertTrue(router.routeDirectiveResult(dispatcher, payload));

            ArgumentCaptor<ToolResult> captor = ArgumentCaptor.forClass(ToolResult.class);
            verify(dispatcher).completeCall(eq("d-bad"), captor.capture());
            assertFalse(captor.getValue().hasStructuredContent(),
                    "Malformed JSON should not have structuredContent");
            assertEquals("{invalid json here", captor.getValue().getContent());
        }
    }

    @Nested
    @DisplayName("NPE 防御")
    class NullSafety {

        @Test
        @DisplayName("null dispatcher 抛 NullPointerException")
        void nullDispatcherThrows() {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("callId", "c1");
            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertThrows(NullPointerException.class,
                    () -> router.routeClientToolResult(null, payload));
        }

        @Test
        @DisplayName("null payload 抛 NullPointerException")
        void nullPayloadThrows() {
            ToolCallCompleter dispatcher = mock(ToolCallCompleter.class);
            ClientToolResultRouter router = new ClientToolResultRouter(mapper, null);
            assertThrows(NullPointerException.class,
                    () -> router.routeClientToolResult(dispatcher, null));
        }
    }
}
