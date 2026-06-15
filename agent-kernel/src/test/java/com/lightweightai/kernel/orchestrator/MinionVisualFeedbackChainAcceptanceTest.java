package com.lightweightai.kernel.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.ContentBlock;
import com.lightweightai.kernel.llm.ImageContent;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.llm.claude.ClaudeProvider;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔴 视觉回灌链 end-to-end payload-capture 验收测试（ADR-010）。
 *
 * 堵住巡检 R-1：此前 {@code look} 的帧 → {@link ImageContent} → 下一轮多模态消息这条链
 * 在生产代码里根本不存在，M0 用纯文本字符串冒充图像掩盖了它。
 *
 * 本测试**全链路真**，不用文本冒充图像：
 *  1. {@code look} 工具返回真实 {@link ImageContent} 帧（base64 字节）；
 *  2. 跑生产 {@link ToolCallingLoop}（reactive 路径），断言**下一轮喂给 LLM 的对话**
 *     里真的出现携带该帧字节的 {@link ImageContent} 块（而非被文本吞掉）；
 *  3. 把该轮对话喂给**真实** {@link ClaudeProvider} / {@link OpenRouterProvider}，
 *     断言出站 HTTP body 含 image block（Claude image source / OpenAI image_url）。
 *
 * 对应 CLAUDE.md UT 规则 3/5：producer={@code look} 的帧 → consumer=下一轮多模态消息，
 * 这条传输对必须有 ≥1 个 payload-capture 端到端测试。
 */
@DisplayName("视觉回灌链 E2E: look 帧 → ImageContent → 下一轮请求体 image block")
class MinionVisualFeedbackChainAcceptanceTest {

    private static final byte[] FRAME = "fake-jpeg-frame-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String FRAME_MIME = "image/jpeg";

    private ObjectMapper mapper;
    private MockWebServer openRouterServer;

    @BeforeEach
    void setUp() throws IOException {
        mapper = new ObjectMapper();
        openRouterServer = new MockWebServer();
        openRouterServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        openRouterServer.shutdown();
    }

    /** look：假摄像头，返回真实 ImageContent 帧（不是文本！）。 */
    private ToolRegistry registryWithLook() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String getName() { return "look"; }
            @Override public String getDescription() { return "看一眼/抓帧"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> a) {
                return ToolResult.image("captured 1 frame", new ImageContent(FRAME, FRAME_MIME));
            }
        });
        return registry;
    }

    /** 跑生产 ToolCallingLoop（reactive），返回第 2 轮（工具结果回灌后）喂给 LLM 的对话。 */
    private List<ConversationMessage> runLoopAndCaptureSecondRound() {
        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "look", Map.of(), "我看到一只橘猫。");
        ToolExecutor executor = new ToolExecutor(registryWithLook());
        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(spy)
                .toolExecutor(executor)
                .maxIterations(5)
                .build();

        loop.executeWithToolsReactive(
                List.of(ConversationMessage.builder()
                        .role(MessageRole.USER).textContent("你看到什么了？").build()),
                LLMOptions.builder().maxTokens(256).build())
            .collectList().block();

        assertTrue(spy.callCount() >= 2,
                "look 应触发第 2 轮 LLM 调用（工具结果回灌）；实际轮数: " + spy.callCount());
        return spy.messagesHistory().get(1);
    }

    private ImageContent findImageBlock(List<ConversationMessage> conversation) {
        for (ConversationMessage msg : conversation) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof ImageContent img) {
                    return img;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("第 1 环：look 帧回灌成下一轮对话里的 ImageContent（不被文本吞掉）")
    void frameReachesNextRoundAsImageContent() {
        List<ConversationMessage> secondRound = runLoopAndCaptureSecondRound();

        ImageContent img = findImageBlock(secondRound);
        assertNotNull(img, "下一轮对话应含 ImageContent 块（视觉回灌链）；实际只有文本: " + secondRound);
        assertFalse(img.isUrl(), "应是 base64 帧");
        assertArrayEquals(FRAME, img.getData(), "回灌的帧字节应与 look 抓到的一致");
        assertEquals(FRAME_MIME, img.getMimeType());
    }

    @Test
    @DisplayName("第 2 环（Claude）：下一轮请求体 messages 含 image(base64 source) block")
    void frameReachesClaudeRequestBody() throws Exception {
        List<ConversationMessage> secondRound = runLoopAndCaptureSecondRound();

        CapturingOkHttp http = new CapturingOkHttp();
        ClaudeProvider provider = new ClaudeProvider("test-key", "claude-3-5-sonnet-20241022", http);
        provider.complete(secondRound, LLMOptions.builder().maxTokens(128).build());

        JsonNode body = http.lastBodyJson(mapper);
        JsonNode imageBlock = firstImageBlock(body.get("messages"), "image");
        assertNotNull(imageBlock, "Claude body 的某条消息 content 数组里应有 image block；body=" + body);
        JsonNode source = imageBlock.get("source");
        assertEquals("base64", source.get("type").asText());
        assertEquals(FRAME_MIME, source.get("media_type").asText());
        assertEquals(Base64.getEncoder().encodeToString(FRAME), source.get("data").asText(),
                "look 抓到的帧字节应 base64 进 Claude 请求体");
    }

    @Test
    @DisplayName("第 2 环（OpenRouter）：下一轮请求体 messages 含 image_url(data URI) block")
    void frameReachesOpenRouterRequestBody() throws Exception {
        List<ConversationMessage> secondRound = runLoopAndCaptureSecondRound();

        openRouterServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"1\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},"
                        + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1}}")
                .setHeader("Content-Type", "application/json"));
        OpenRouterProvider provider = new OpenRouterProvider(
                "test-key", "test-model", openRouterServer.url("/v1").toString());

        provider.complete(secondRound, LLMOptions.builder().maxTokens(128).build());

        RecordedRequest req = openRouterServer.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        JsonNode imageBlock = firstImageBlock(body.get("messages"), "image_url");
        assertNotNull(imageBlock, "OpenRouter body 的某条消息 content 数组里应有 image_url block；body=" + body);
        String expectedUri = "data:" + FRAME_MIME + ";base64," + Base64.getEncoder().encodeToString(FRAME);
        assertEquals(expectedUri, imageBlock.get("image_url").get("url").asText(),
                "look 抓到的帧应作为 data URI 进 OpenRouter 请求体");
    }

    /** 在 messages 各条 content 数组里找第一个 type == wantType 的块。 */
    private JsonNode firstImageBlock(JsonNode messages, String wantType) {
        for (JsonNode msg : messages) {
            JsonNode content = msg.get("content");
            if (content == null || !content.isArray()) continue;
            for (JsonNode block : content) {
                if (block.has("type") && wantType.equals(block.get("type").asText())) {
                    return block;
                }
            }
        }
        return null;
    }

    /** 拦截外发 Request body，返回最小 200 让 complete() 成功（镜像 ClaudeProviderMultimodalTest）。 */
    private static class CapturingOkHttp extends OkHttpClient {
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
                    String stub = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                            + "\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],"
                            + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
                    return new Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200).message("OK")
                            .body(ResponseBody.create(stub, MediaType.get("application/json")))
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
