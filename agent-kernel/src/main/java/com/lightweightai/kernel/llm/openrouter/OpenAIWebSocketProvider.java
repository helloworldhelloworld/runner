package com.lightweightai.kernel.llm.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ToolCall;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI-compatible LLM Provider using HTTP POST with text/plain content type.
 *
 * Sends OpenAI chat/completions format JSON via HTTP POST (text/plain),
 * receives SSE streaming deltas or plain JSON responses.
 *
 * Compatible with custom gateways that accept text/plain POST requests.
 */
public class OpenAIWebSocketProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIWebSocketProvider.class);
    private static final MediaType TEXT_PLAIN = MediaType.parse("text/plain; charset=utf-8");

    private final String requestUrl;
    private final String model;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelCapability modelCapability;

    public OpenAIWebSocketProvider(String websocketUrl, String model) {
        this(websocketUrl, model, null);
    }

    public OpenAIWebSocketProvider(String websocketUrl, String model, String apiKey) {
        if (websocketUrl == null || websocketUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }

        // Convert ws(s):// to http(s):// if needed
        String url = websocketUrl.trim()
            .replaceFirst("^ws://", "http://")
            .replaceFirst("^wss://", "https://")
            .replaceAll("/+$", "");
        // Ensure URL ends with /chat/completions
        if (!url.endsWith("/chat/completions")) {
            url = url + "/chat/completions";
        }
        this.requestUrl = url;
        this.model = model;
        this.apiKey = apiKey != null ? apiKey : "";
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
        this.modelCapability = new OpenRouterModelCapability(model);

        logger.info("Initialized OpenAI HTTP Provider (text/plain): url={}, model={}", this.requestUrl, model);
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        try {
            String body = buildRequestBody(messages, options, false);
            Request request = buildHttpRequest(body);
            logger.debug("HTTP complete: POST {} (model: {})", requestUrl, model);

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    logger.error("HTTP POST {} → {} \nError: {}", requestUrl, response.code(), errorBody);
                    throw new RuntimeException("LLM API call failed: " + response.code() +
                        " (url: " + requestUrl + ")\nError: " + errorBody);
                }
                String responseBody = response.body().string();
                logger.debug("HTTP response: {}", responseBody);
                return parseResponse(responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException("LLM HTTP call failed", e);
        }
    }

    @Override
    public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
        return CompletableFuture.supplyAsync(() -> complete(messages, options));
    }

    @Override
    public CompletableFuture<LLMResponse> completeStream(
        List<ConversationMessage> messages,
        LLMOptions options,
        StreamEventHandler handler
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = buildRequestBody(messages, options, true);
                Request request = buildHttpRequest(body);
                logger.info("HTTP streaming: POST {} (model: {})", requestUrl, model);

                handler.onStart();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error details";
                        logger.error("HTTP streaming POST {} → {} \nError: {}", requestUrl, response.code(), errorBody);
                        throw new RuntimeException("LLM streaming failed: " + response.code() +
                            " (url: " + requestUrl + ")\nError: " + errorBody);
                    }

                    LLMResponse finalResponse = parseStreamingResponse(response.body(), handler);
                    logger.info("HTTP stream complete: textLen={}, toolCalls={}, stopReason={}",
                        finalResponse.getMessage().getTextContent() != null
                            ? finalResponse.getMessage().getTextContent().length() : 0,
                        finalResponse.hasToolCalls() ? finalResponse.getToolCalls().size() : 0,
                        finalResponse.getStopReason());

                    handler.onComplete(finalResponse);
                    return finalResponse;
                }
            } catch (Exception e) {
                handler.onError(e);
                throw new RuntimeException("HTTP streaming failed", e);
            }
        });
    }

    @Override
    public Flux<StreamEvent> completeStreamReactive(
            List<ConversationMessage> messages, LLMOptions options) {
        return LLMProvider.super.completeStreamReactive(messages, options)
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public ModelCapability getModelCapability() {
        return modelCapability;
    }

    @Override
    public String getProviderName() {
        return "openai-ws";
    }

    // ─── HTTP Request Building ───────────────────────────────────────

    private Request buildHttpRequest(String body) {
        Request.Builder builder = new Request.Builder()
            .url(requestUrl)
            .post(RequestBody.create(body, TEXT_PLAIN));
        if (!apiKey.isEmpty()) {
            builder.header("api_key", apiKey);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private String buildRequestBody(List<ConversationMessage> messages, LLMOptions options, boolean stream)
            throws IOException {
        ObjectNode root = objectMapper.createObjectNode();

        // api_key in body (for custom gateways)
        if (!apiKey.isEmpty()) {
            root.put("api_key", apiKey);
        }

        root.put("model", model);

        int maxTokens = options != null && options.getMaxTokens() != null
            ? options.getMaxTokens()
            : 4096;
        root.put("max_tokens", maxTokens);

        if (options != null && options.getTemperature() != null) {
            root.put("temperature", options.getTemperature());
        }

        if (stream) {
            root.put("stream", true);
        }

        // Messages
        ArrayNode messagesArray = objectMapper.createArrayNode();
        for (ConversationMessage msg : messages) {
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", convertRole(msg.getRole()));

            Map<String, Object> metadata = msg.getMetadata();

            if (msg.getRole() == MessageRole.ASSISTANT && metadata.containsKey("tool_calls")) {
                String text = msg.getTextContent();
                if (text != null && !text.isEmpty()) {
                    messageNode.put("content", text);
                } else {
                    messageNode.putNull("content");
                }
                List<ToolCall> toolCalls = (List<ToolCall>) metadata.get("tool_calls");
                ArrayNode toolCallsArray = objectMapper.createArrayNode();
                for (ToolCall tc : toolCalls) {
                    ObjectNode tcNode = objectMapper.createObjectNode();
                    tcNode.put("id", tc.getId());
                    tcNode.put("type", "function");
                    ObjectNode fnNode = objectMapper.createObjectNode();
                    fnNode.put("name", tc.getName());
                    fnNode.put("arguments", objectMapper.writeValueAsString(tc.getArguments()));
                    tcNode.set("function", fnNode);
                    toolCallsArray.add(tcNode);
                }
                messageNode.set("tool_calls", toolCallsArray);
            } else if (msg.getRole() == MessageRole.TOOL) {
                messageNode.put("content", msg.getTextContent());
                if (metadata.containsKey("tool_use_id")) {
                    messageNode.put("tool_call_id", (String) metadata.get("tool_use_id"));
                }
            } else {
                messageNode.put("content", msg.getTextContent());
            }

            messagesArray.add(messageNode);
        }
        root.set("messages", messagesArray);

        // Tools (OpenAI function format)
        if (options != null && options.getToolDefinitions() != null && !options.getToolDefinitions().isEmpty()) {
            ArrayNode toolsArray = objectMapper.createArrayNode();
            for (Map<String, Object> toolDef : options.getToolDefinitions()) {
                ObjectNode toolNode = objectMapper.createObjectNode();

                if ("function".equals(toolDef.get("type")) && toolDef.containsKey("function")) {
                    Map<String, Object> function = (Map<String, Object>) toolDef.get("function");
                    String name = (String) function.get("name");
                    if (name == null || name.isEmpty()) continue;
                    toolNode.put("type", "function");
                    toolNode.set("function", objectMapper.valueToTree(function));
                } else {
                    String name = (String) toolDef.get("name");
                    if (name == null || name.isEmpty()) continue;
                    toolNode.put("type", "function");
                    ObjectNode functionNode = objectMapper.createObjectNode();
                    functionNode.put("name", name);
                    String description = (String) toolDef.get("description");
                    functionNode.put("description", description != null ? description : "");
                    Object schema = toolDef.getOrDefault("input_schema", toolDef.get("parameters"));
                    if (schema != null) {
                        functionNode.set("parameters", objectMapper.valueToTree(schema));
                    }
                    toolNode.set("function", functionNode);
                }
                toolsArray.add(toolNode);
            }
            if (toolsArray.size() > 0) {
                root.set("tools", toolsArray);
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    private String convertRole(MessageRole role) {
        switch (role) {
            case SYSTEM:  return "system";
            case USER:    return "user";
            case ASSISTANT: return "assistant";
            case TOOL:    return "tool";
            default:      throw new IllegalArgumentException("Unsupported role: " + role);
        }
    }

    // ─── Response Parsing ────────────────────────────────────────────

    private LLMResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.isTextual()) {
            logger.warn("Response is double-encoded JSON string, unwrapping");
            root = objectMapper.readTree(root.asText());
        }

        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            logger.error("No choices in response. Raw: {}", responseBody);
            throw new RuntimeException("No choices in response: " + responseBody);
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.get("message");

        String content = "";
        JsonNode contentNode = message.get("content");
        if (contentNode != null && !contentNode.isNull()) {
            content = contentNode.asText();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = message.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                String id = tc.get("id").asText();
                JsonNode functionNode = tc.get("function");
                String name = functionNode.get("name").asText();
                String argsJson = functionNode.get("arguments").asText();
                @SuppressWarnings("unchecked")
                Map<String, Object> argsMap = objectMapper.readValue(argsJson, Map.class);
                toolCalls.add(new ToolCall(id, name, argsMap));
            }
        }

        ConversationMessage responseMessage = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent(content)
            .build();

        JsonNode usage = root.get("usage");
        LLMResponse.UsageInfo usageInfo = null;
        if (usage != null) {
            int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
            usageInfo = new LLMResponse.UsageInfo(promptTokens, completionTokens);
        }

        String finishReason = firstChoice.has("finish_reason") ? firstChoice.get("finish_reason").asText() : null;

        return LLMResponse.builder()
            .message(responseMessage)
            .toolCalls(toolCalls)
            .stopReason(finishReason)
            .usage(usageInfo)
            .build();
    }

    /**
     * Parse streaming response — handles both SSE format and plain JSON fallback.
     */
    private LLMResponse parseStreamingResponse(ResponseBody responseBody, StreamEventHandler handler) throws IOException {
        StringBuilder textContent = new StringBuilder();
        String finishReason = null;
        Map<Integer, ToolCallAccumulator> toolCallMap = new LinkedHashMap<>();

        StringBuilder rawBody = new StringBuilder();
        boolean isSse = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rawBody.append(line).append('\n');

                if (line.startsWith("data: ")) {
                    isSse = true;
                    String data = line.substring(6).trim();

                    if (data.equals("[DONE]")) {
                        logger.debug("SSE stream: [DONE]");
                        break;
                    }

                    try {
                        JsonNode eventData = objectMapper.readTree(data);

                        // Handle double-encoded JSON
                        if (eventData.isTextual()) {
                            eventData = objectMapper.readTree(eventData.asText());
                        }

                        JsonNode choices = eventData.get("choices");
                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                            JsonNode firstChoice = choices.get(0);
                            JsonNode delta = firstChoice.get("delta");

                            if (delta != null) {
                                // Text content
                                if (delta.has("content") && !delta.get("content").isNull()) {
                                    String deltaContent = delta.get("content").asText();
                                    if (!deltaContent.isEmpty()) {
                                        textContent.append(deltaContent);
                                        handler.onTextDelta(deltaContent);
                                    }
                                }

                                // Tool call chunks
                                JsonNode tcArr = delta.get("tool_calls");
                                if (tcArr != null && tcArr.isArray()) {
                                    for (JsonNode tc : tcArr) {
                                        int idx = tc.has("index") ? tc.get("index").asInt() : 0;
                                        ToolCallAccumulator acc = toolCallMap.computeIfAbsent(
                                            idx, i -> new ToolCallAccumulator());
                                        if (tc.has("id")) acc.id = tc.get("id").asText();
                                        JsonNode fn = tc.get("function");
                                        if (fn != null) {
                                            if (fn.has("name")) acc.name = fn.get("name").asText();
                                            if (fn.has("arguments")) acc.argsJson.append(fn.get("arguments").asText());
                                        }
                                    }
                                }
                            }

                            // Non-streaming "message" format (single-frame complete response)
                            JsonNode message = firstChoice.get("message");
                            if (message != null) {
                                JsonNode contentNode = message.get("content");
                                if (contentNode != null && !contentNode.isNull()) {
                                    String content = contentNode.asText();
                                    if (!content.isEmpty()) {
                                        textContent.append(content);
                                        handler.onTextDelta(content);
                                    }
                                }
                                JsonNode toolCallsNode = message.get("tool_calls");
                                if (toolCallsNode != null && toolCallsNode.isArray()) {
                                    for (int i = 0; i < toolCallsNode.size(); i++) {
                                        JsonNode tc = toolCallsNode.get(i);
                                        ToolCallAccumulator acc = toolCallMap.computeIfAbsent(
                                            i, k -> new ToolCallAccumulator());
                                        acc.id = tc.get("id").asText();
                                        JsonNode fn = tc.get("function");
                                        acc.name = fn.get("name").asText();
                                        acc.argsJson.append(fn.get("arguments").asText());
                                    }
                                }
                            }

                            if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isNull()) {
                                finishReason = firstChoice.get("finish_reason").asText();
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse SSE event: {}", e.getMessage());
                    }
                }
            }
        }

        // Non-SSE fallback: parse as plain JSON response
        if (!isSse) {
            logger.info("Response is not SSE format, parsing as plain JSON");
            try {
                LLMResponse fallbackResponse = parseResponse(rawBody.toString().trim());
                String text = fallbackResponse.getMessage().getTextContent();
                if (text != null && !text.isEmpty()) {
                    handler.onTextDelta(text);
                }
                return fallbackResponse;
            } catch (Exception e) {
                logger.error("Failed to parse non-SSE response: {}", e.getMessage());
            }
        }

        // Assemble tool calls
        List<ToolCall> toolCalls = new ArrayList<>();
        for (ToolCallAccumulator acc : toolCallMap.values()) {
            try {
                String argsStr = acc.argsJson.toString();
                @SuppressWarnings("unchecked")
                Map<String, Object> argsMap = argsStr.isBlank()
                    ? new HashMap<>()
                    : objectMapper.readValue(argsStr, Map.class);
                toolCalls.add(new ToolCall(acc.id, acc.name, argsMap));
            } catch (Exception e) {
                logger.warn("Failed to parse tool call args: {}", e.getMessage());
            }
        }

        ConversationMessage message = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent(textContent.toString())
            .build();

        return LLMResponse.builder()
            .message(message)
            .toolCalls(toolCalls)
            .stopReason(finishReason)
            .build();
    }

    private static class ToolCallAccumulator {
        String id = "";
        String name = "";
        StringBuilder argsJson = new StringBuilder();
    }
}
