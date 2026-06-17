package com.lightweightai.kernel.llm.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.ImageContent;
import com.lightweightai.kernel.llm.LLMOptions;
import okhttp3.*;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R1（多模态接线）payload-capture acceptance test —— 断言带 {@link ImageContent} 的
 * 消息真正被写进外发 HTTP body 的 Claude content 数组（image block），而不是被 getTextContent() 吞掉。
 *
 * 对应 CLAUDE.md "Assert the payload, not just the ceremony" / 传输链测试范式。
 * Claude content block 形态：
 *   base64: {"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":"..."}}
 *   url:    {"type":"image","source":{"type":"url","url":"https://..."}}
 */
@DisplayName("ClaudeProvider - 多模态 image content 序列化")
class ClaudeProviderMultimodalTest {

    private CapturingHttpClient capturing;
    private ClaudeProvider provider;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        capturing = new CapturingHttpClient();
        provider = new ClaudeProvider("test-key", "claude-3-5-sonnet-20241022", capturing);
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("base64 图片 → content 数组含 text + image(base64 source)")
    void base64ImageBecomesImageBlock() throws Exception {
        byte[] pixels = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        ConversationMessage msg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("这是什么?")
                .addContent(new ImageContent(pixels, "image/jpeg"))
                .build();

        provider.complete(List.of(msg), LLMOptions.builder().maxTokens(128).build());

        JsonNode content = capturing.lastBodyJson(mapper).get("messages").get(0).get("content");
        assertTrue(content.isArray(), "含图片的消息 content 应为数组；实际: " + content);
        assertEquals(2, content.size(), "应有 text + image 两个块");

        JsonNode text = content.get(0);
        assertEquals("text", text.get("type").asText());
        assertEquals("这是什么?", text.get("text").asText());

        JsonNode image = content.get(1);
        assertEquals("image", image.get("type").asText());
        JsonNode source = image.get("source");
        assertEquals("base64", source.get("type").asText());
        assertEquals("image/jpeg", source.get("media_type").asText());
        assertEquals(Base64.getEncoder().encodeToString(pixels), source.get("data").asText(),
                "图片字节应 base64 编码进 data");
    }

    @Test
    @DisplayName("URL 图片 → image block 用 url source")
    void urlImageBecomesUrlSource() throws Exception {
        ConversationMessage msg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("describe")
                .addContent(new ImageContent("https://example.com/cat.png", "image/png"))
                .build();

        provider.complete(List.of(msg), LLMOptions.builder().maxTokens(128).build());

        JsonNode content = capturing.lastBodyJson(mapper).get("messages").get(0).get("content");
        assertTrue(content.isArray());
        JsonNode source = content.get(1).get("source");
        assertEquals("url", source.get("type").asText());
        assertEquals("https://example.com/cat.png", source.get("url").asText());
    }

    @Test
    @DisplayName("纯文本消息保持 content 为字符串（向后兼容）")
    void textOnlyStaysString() throws Exception {
        ConversationMessage msg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("hi")
                .build();

        provider.complete(List.of(msg), LLMOptions.builder().maxTokens(128).build());

        JsonNode content = capturing.lastBodyJson(mapper).get("messages").get(0).get("content");
        assertTrue(content.isTextual(), "纯文本应保持字符串形态；实际: " + content);
        assertEquals("hi", content.asText());
    }

    /**
     * L-2：流式发射路径同样把 image block 写进请求体。
     *
     * 此前只断言了同步 complete()；completeStream()（completeStreamReactive 默认桥接它）走
     * buildRequestBody + {@code stream:true} 经 newCall().execute() 发出——之前无断言。
     * 断言：流式 body 的 messages[0].content 含 image(base64 source) 且 root.stream==true。
     */
    @Test
    @DisplayName("流式路径 completeStream → 请求体含 image(base64) block 且 stream=true")
    void streamingPathAlsoSerializesImageBlock() throws Exception {
        byte[] pixels = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        ConversationMessage msg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("这是什么?")
                .addContent(new ImageContent(pixels, "image/jpeg"))
                .build();

        // handler=null：stub 响应非 SSE，解析器跳过所有行→空文本完成；body 已在 newCall 捕获。
        provider.completeStream(List.of(msg), LLMOptions.builder().maxTokens(128).build(), null)
                .get(5, TimeUnit.SECONDS);

        JsonNode body = capturing.lastBodyJson(mapper);
        assertTrue(body.get("stream").asBoolean(), "流式请求体应带 stream=true");

        JsonNode content = body.get("messages").get(0).get("content");
        assertTrue(content.isArray(), "含图片的消息 content 应为数组；实际: " + content);
        JsonNode image = content.get(1);
        assertEquals("image", image.get("type").asText());
        JsonNode source = image.get("source");
        assertEquals("base64", source.get("type").asText());
        assertEquals("image/jpeg", source.get("media_type").asText());
        assertEquals(Base64.getEncoder().encodeToString(pixels), source.get("data").asText(),
                "流式路径也应把图片字节 base64 进 data");
    }

    /** Captures the outgoing Request body, returns a minimal 200 response so complete() succeeds. */
    private static class CapturingHttpClient extends OkHttpClient {
        private final AtomicReference<String> lastBody = new AtomicReference<>();

        JsonNode lastBodyJson(ObjectMapper mapper) throws IOException {
            String raw = lastBody.get();
            assertNotNull(raw, "no request was captured");
            return mapper.readTree(raw);
        }

        @Override
        public Call newCall(Request request) {
            try {
                Buffer buf = new Buffer();
                if (request.body() != null) request.body().writeTo(buf);
                lastBody.set(buf.readUtf8());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new Call() {
                @Override public Request request() { return request; }
                @Override public Response execute() {
                    String stubResponse = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                            + "\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],"
                            + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
                    return new Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200).message("OK")
                            .body(ResponseBody.create(stubResponse, MediaType.get("application/json")))
                            .build();
                }
                @Override public void enqueue(Callback callback) {}
                @Override public void cancel() {}
                @Override public boolean isExecuted() { return true; }
                @Override public boolean isCanceled() { return false; }
                @Override public Call clone() { return this; }
                @Override public okio.Timeout timeout() { return okio.Timeout.NONE; }
            };
        }
    }
}
