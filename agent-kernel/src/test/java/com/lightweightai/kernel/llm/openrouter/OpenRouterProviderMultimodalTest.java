package com.lightweightai.kernel.llm.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.ImageContent;
import com.lightweightai.kernel.llm.LLMOptions;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R1b（多模态接线 · OpenRouter）payload-capture acceptance test —— 断言带 {@link ImageContent}
 * 的消息被写进外发请求体的 OpenAI 风格 content 数组（image_url block），而非被 getTextContent() 吞掉。
 *
 * OpenAI/OpenRouter content block 形态：
 *   base64: {"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}
 *   url:    {"type":"image_url","image_url":{"url":"https://..."}}
 */
@DisplayName("OpenRouterProvider - 多模态 image_url 序列化")
class OpenRouterProviderMultimodalTest {

    private MockWebServer server;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private OpenRouterProvider provider() {
        return new OpenRouterProvider("test-key", "test-model", server.url("/v1").toString());
    }

    private void enqueueOk() {
        server.enqueue(new MockResponse()
                .setBody("{\"id\":\"1\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},"
                        + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1}}")
                .setHeader("Content-Type", "application/json"));
    }

    private JsonNode userContent() throws Exception {
        RecordedRequest req = server.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        return body.get("messages").get(0).get("content");
    }

    @Test
    @DisplayName("base64 图片 → content 数组含 text + image_url(data URI)")
    void base64ImageBecomesImageUrlBlock() throws Exception {
        enqueueOk();
        byte[] pixels = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        ConversationMessage msg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("这是什么?")
                .addContent(new ImageContent(pixels, "image/jpeg"))
                .build();

        provider().complete(List.of(msg), LLMOptions.builder().maxTokens(128).build());

        JsonNode content = userContent();
        assertTrue(content.isArray(), "含图片的消息 content 应为数组；实际: " + content);
        assertEquals(2, content.size());

        assertEquals("text", content.get(0).get("type").asText());
        assertEquals("这是什么?", content.get(0).get("text").asText());

        JsonNode image = content.get(1);
        assertEquals("image_url", image.get("type").asText());
        String expectedUri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(pixels);
        assertEquals(expectedUri, image.get("image_url").get("url").asText());
    }

    @Test
    @DisplayName("URL 图片 → image_url 直接用 url")
    void urlImageUsesUrlDirectly() throws Exception {
        enqueueOk();
        ConversationMessage msg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("describe")
                .addContent(new ImageContent("https://example.com/cat.png", "image/png"))
                .build();

        provider().complete(List.of(msg), LLMOptions.builder().maxTokens(128).build());

        JsonNode content = userContent();
        assertTrue(content.isArray());
        assertEquals("image_url", content.get(1).get("type").asText());
        assertEquals("https://example.com/cat.png",
                content.get(1).get("image_url").get("url").asText());
    }

    @Test
    @DisplayName("纯文本消息保持 content 为字符串（向后兼容）")
    void textOnlyStaysString() throws Exception {
        enqueueOk();
        ConversationMessage msg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("hi")
                .build();

        provider().complete(List.of(msg), LLMOptions.builder().maxTokens(128).build());

        JsonNode content = userContent();
        assertTrue(content.isTextual(), "纯文本应保持字符串；实际: " + content);
        assertEquals("hi", content.asText());
    }
}
