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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI-compatible LLM Provider over WebSocket transport.
 *
 * Sends OpenAI chat/completions format JSON as WebSocket text frames,
 * receives multi-frame streaming deltas in OpenAI format.
 *
 * Each LLM call opens a new WebSocket connection (request-scoped).
 */
public class OpenAIWebSocketProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIWebSocketProvider.class);

    private final String websocketUrl;
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
            throw new IllegalArgumentException("WebSocket URL cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }

        // OkHttp WebSocket 需要 ws:// 或 wss:// scheme；
        // 用户可能传 http:// 或 https://，这里自动转换
        String normalizedUrl = websocketUrl.trim().replaceAll("/+$", "");
        if (normalizedUrl.startsWith("http://")) {
            normalizedUrl = "ws://" + normalizedUrl.substring(7);
        } else if (normalizedUrl.startsWith("https://")) {
            normalizedUrl = "wss://" + normalizedUrl.substring(8);
        }
        this.websocketUrl = normalizedUrl;
        this.model = model;
        this.apiKey = apiKey != null ? apiKey : "";
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
        this.modelCapability = new OpenRouterModelCapability(model);

        logger.info("Initialized OpenAI WebSocket Provider: url={}, model={}", this.websocketUrl, model);
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        try {
            return completeAsync(messages, options).get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("WebSocket LLM call failed", e);
        }
    }

    @Override
    public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestBody = buildRequestBody(messages, options, false);
                CompletableFuture<String> responseFuture = new CompletableFuture<>();

                Request wsRequest = buildWsRequest();
                WebSocket ws = httpClient.newWebSocket(wsRequest, new WebSocketListener() {
                    private final StringBuilder responseBuffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        logger.debug("WS connected for sync request, sending payload");
                        webSocket.send(requestBody);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        responseBuffer.append(text);
                    }

                    @Override
                    public void onClosing(WebSocket webSocket, int code, String reason) {
                        // 必须回复 close，否则 onClosed 不会被调用
                        webSocket.close(1000, null);
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        responseFuture.complete(responseBuffer.toString());
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        responseFuture.completeExceptionally(t);
                    }
                });

                String responseBody = responseFuture.get(120, TimeUnit.SECONDS);
                return parseResponse(responseBody);

            } catch (Exception e) {
                throw new RuntimeException("WebSocket LLM call failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<LLMResponse> completeStream(
        List<ConversationMessage> messages,
        LLMOptions options,
        StreamEventHandler handler
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestBody = buildRequestBody(messages, options, true);
                int toolCount = options != null && options.getToolDefinitions() != null
                    ? options.getToolDefinitions().size() : 0;
                logger.info("WS streaming: {} messages, {} tools, model={}",
                    messages.size(), toolCount, model);
                logger.debug("WS streaming request body: {}", requestBody);

                StringBuilder textContent = new StringBuilder();
                Map<Integer, ToolCallAccumulator> toolCallMap = new LinkedHashMap<>();
                String[] finishReason = {null};

                CompletableFuture<Void> doneFuture = new CompletableFuture<>();

                Request wsRequest = buildWsRequest();
                logger.info("WS streaming: connecting to {}", websocketUrl);

                WebSocket ws = httpClient.newWebSocket(wsRequest, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        logger.info("WS connected for streaming, sending request");
                        handler.onStart();
                        webSocket.send(requestBody);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        String trimmed = text.trim();
                        logger.debug("WS frame: {}", trimmed.length() > 500
                            ? trimmed.substring(0, 500) + "..." : trimmed);

                        // Handle [DONE] signal
                        if ("[DONE]".equals(trimmed)) {
                            logger.debug("WS stream: received [DONE]");
                            webSocket.close(1000, "done");
                            return;
                        }

                        // Strip SSE prefix if present (some backends send SSE-style over WS)
                        if (trimmed.startsWith("data: ")) {
                            trimmed = trimmed.substring(6).trim();
                            if ("[DONE]".equals(trimmed)) {
                                logger.debug("WS stream: received data: [DONE]");
                                webSocket.close(1000, "done");
                                return;
                            }
                        }

                        try {
                            JsonNode eventData = objectMapper.readTree(trimmed);

                            // Handle double-encoded JSON
                            if (eventData.isTextual()) {
                                logger.warn("WS: double-encoded JSON, unwrapping");
                                eventData = objectMapper.readTree(eventData.asText());
                            }

                            JsonNode choices = eventData.get("choices");
                            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                                // Could be a non-streaming full response
                                if (eventData.has("message") || eventData.has("error")) {
                                    logger.debug("WS: received non-delta message, treating as full response");
                                }
                                return;
                            }

                            JsonNode firstChoice = choices.get(0);

                            // Streaming delta format
                            JsonNode delta = firstChoice.get("delta");
                            if (delta != null) {
                                // Reasoning/thinking content (GLM-5, DeepSeek etc.)
                                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                                    String reasoning = delta.get("reasoning_content").asText();
                                    if (!reasoning.isEmpty()) {
                                        handler.onTextDelta(reasoning);
                                    }
                                }

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
                                // Parse complete tool_calls from message
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

                            // finish_reason
                            if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isNull()) {
                                finishReason[0] = firstChoice.get("finish_reason").asText();
                            }

                        } catch (Exception e) {
                            logger.warn("WS: failed to parse frame: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onClosing(WebSocket webSocket, int code, String reason) {
                        webSocket.close(1000, null);
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        logger.debug("WS stream closed: {} {}", code, reason);
                        doneFuture.complete(null);
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        logger.error("WS stream failure: {}", t.getMessage());
                        doneFuture.completeExceptionally(t);
                    }
                });

                // Wait for stream to complete
                doneFuture.get(120, TimeUnit.SECONDS);

                // Assemble tool calls
                List<ToolCall> toolCalls = assembleToolCalls(toolCallMap);

                // Build final response
                ConversationMessage responseMessage = ConversationMessage.builder()
                    .role(MessageRole.ASSISTANT)
                    .textContent(textContent.toString())
                    .build();

                LLMResponse response = LLMResponse.builder()
                    .message(responseMessage)
                    .toolCalls(toolCalls)
                    .stopReason(finishReason[0])
                    .build();

                logger.info("WS stream complete: textLen={}, toolCalls={}, stopReason={}",
                    textContent.length(), toolCalls.size(), finishReason[0]);
                if (!toolCalls.isEmpty()) {
                    logger.info("WS tool calls: {}",
                        toolCalls.stream().map(tc -> tc.getName() + "(" + tc.getId() + ")").toList());
                }

                handler.onComplete(response);
                return response;

            } catch (Exception e) {
                handler.onError(e);
                throw new RuntimeException("WebSocket streaming failed", e);
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

    // ─── Request Building (OpenAI format) ───────────────────────────

    private Request buildWsRequest() {
        Request.Builder builder = new Request.Builder().url(websocketUrl);
        // 自定义网关：仅用 api_key header 认证，不发 Authorization: Bearer
        // （api_key 同时也在 JSON body 中传递）
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

    // ─── Response Parsing (OpenAI format) ───────────────────────────

    private LLMResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.isTextual()) {
            logger.warn("Response is double-encoded JSON string, unwrapping");
            root = objectMapper.readTree(root.asText());
        }

        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            logger.error("No choices in WS response. Raw: {}", responseBody);
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

    private List<ToolCall> assembleToolCalls(Map<Integer, ToolCallAccumulator> toolCallMap) {
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
        if (!toolCalls.isEmpty()) {
            logger.debug("Assembled {} tool calls from WS stream", toolCalls.size());
        }
        return toolCalls;
    }

    private static class ToolCallAccumulator {
        String id = "";
        String name = "";
        StringBuilder argsJson = new StringBuilder();
    }
}
