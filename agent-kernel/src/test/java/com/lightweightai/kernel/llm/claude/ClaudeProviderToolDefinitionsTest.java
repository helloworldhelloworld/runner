package com.lightweightai.kernel.llm.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import okhttp3.*;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 断言 ClaudeProvider 正确把 LLMOptions.toolDefinitions 写入外发 HTTP body 的 tools 数组。
 *
 * 这是 CLAUDE.md 里"Assert the payload, not just the ceremony"规则的专门测试：
 * 对每个 provider 都要有一条"empty → body 不含 tools 键"和"非空 → body 含正确 tools"的测试。
 */
@DisplayName("ClaudeProvider - 请求体 toolDefinitions 序列化")
class ClaudeProviderToolDefinitionsTest {

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
    @DisplayName("toolDefinitions 非空时，body 包含正确结构的 tools 数组")
    void toolDefinitionsArePresentInBody() throws Exception {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("city", Map.of("type", "string")));

        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("name", "get_weather");
        toolDef.put("description", "Get weather for a city");
        toolDef.put("input_schema", schema);

        LLMOptions options = LLMOptions.builder()
                .maxTokens(128)
                .toolDefinitions(List.of(toolDef))
                .build();

        provider.complete(singleUserMessage("weather?"), options);

        JsonNode body = capturing.lastBodyJson(mapper);
        assertTrue(body.has("tools"), "body 应含 tools 键；实际: " + body);
        JsonNode tools = body.get("tools");
        assertEquals(1, tools.size());
        JsonNode tool = tools.get(0);
        assertEquals("get_weather", tool.get("name").asText());
        assertEquals("Get weather for a city", tool.get("description").asText());
        assertTrue(tool.has("input_schema"), "Claude 使用 input_schema 字段");
        assertEquals("object", tool.get("input_schema").get("type").asText());
    }

    @Test
    @DisplayName("toolDefinitions 为空时，body 不应含 tools 键")
    void emptyToolDefinitionsOmitsToolsKey() throws Exception {
        LLMOptions options = LLMOptions.builder().maxTokens(128).build();

        provider.complete(singleUserMessage("hi"), options);

        JsonNode body = capturing.lastBodyJson(mapper);
        assertFalse(body.has("tools"),
                "空 toolDefinitions 不应在 body 里留空 tools 数组；实际: " + body);
    }

    @Test
    @DisplayName("多个 toolDefinitions 全部进入 tools 数组且保持顺序")
    void multipleToolDefinitionsPreservedInOrder() throws Exception {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (String name : List.of("first", "second", "third")) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("name", name);
            def.put("description", name + " desc");
            def.put("input_schema", Map.of("type", "object"));
            defs.add(def);
        }

        LLMOptions options = LLMOptions.builder()
                .maxTokens(128)
                .toolDefinitions(defs)
                .build();

        provider.complete(singleUserMessage("hi"), options);

        JsonNode body = capturing.lastBodyJson(mapper);
        JsonNode tools = body.get("tools");
        assertEquals(3, tools.size());
        assertEquals("first", tools.get(0).get("name").asText());
        assertEquals("second", tools.get(1).get("name").asText());
        assertEquals("third", tools.get(2).get("name").asText());
    }

    private static List<ConversationMessage> singleUserMessage(String text) {
        return List.of(ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent(text)
                .build());
    }

    /**
     * Captures the outgoing Request body, returns a minimal 200 response so complete() succeeds.
     */
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
