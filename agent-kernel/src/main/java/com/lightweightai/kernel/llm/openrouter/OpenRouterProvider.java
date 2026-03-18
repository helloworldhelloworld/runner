package com.lightweightai.kernel.llm.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenRouter Provider - Unified API for multiple LLM models
 *
 * OpenRouter provides access to Claude, GPT-4, and other models through a single API.
 * API is compatible with OpenAI format.
 */
public class OpenRouterProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenRouterProvider.class);
    private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";
    private static final MediaType JSON = MediaType.get("application/json");

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelCapability modelCapability;

    /**
     * Create a new OpenRouterProvider with default base URL
     *
     * @param apiKey OpenRouter API key
     * @param model Model name (e.g., "anthropic/claude-3.5-sonnet")
     */
    public OpenRouterProvider(String apiKey, String model) {
        this(apiKey, model, null);
    }

    /**
     * Create a new OpenRouterProvider with custom base URL
     *
     * @param apiKey API key (can be empty for services that don't require auth)
     * @param model Model name (e.g., "ZhipuAI/GLM-5")
     * @param baseUrl Custom API base URL (e.g., "http://10.32.101.24:8086/llm/openai"), null for default
     */
    public OpenRouterProvider(String apiKey, String model, String baseUrl) {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }

        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model;
        this.baseUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
        // 使用系统默认设置，添加超时
        // 注意：使用系统代理以避免地区限制
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
        this.modelCapability = new OpenRouterModelCapability(model);

        logger.info("Initialized with model: {}, baseUrl: {}", model, this.baseUrl);
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        try {
            // Build request
            String requestBody = buildRequestBody(messages, options, false);
            logger.debug("Calling API with {} messages", messages.size());

            // Make HTTP request
            String requestUrl = baseUrl + "/chat/completions";
            Request.Builder reqBuilder = new Request.Builder()
                .url(requestUrl)
                .post(RequestBody.create(requestBody, JSON));
            if (!isCustomBaseUrl()) {
                // 标准 OpenRouter 需要的额外 header
                reqBuilder.header("HTTP-Referer", "https://github.com/lightweightai/kernel");
                reqBuilder.header("X-Title", "Lightweight AI Kernel");
            }
            if (!apiKey.isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }
            Request request = reqBuilder.build();
            logger.info("OpenRouter API request: POST {} (model: {})", requestUrl, model);
            logger.debug("OpenRouter request body: {}", requestBody);
            try (Response response = httpClient.newCall(request).execute()) {
                logger.debug("Got response: {}", response.code());
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    logger.error("OpenRouter API failed: POST {} → HTTP {} \nError: {}", requestUrl, response.code(), errorBody);
                    throw new RuntimeException("OpenRouter API call failed: " + response.code() +
                                             " (url: " + requestUrl + ")" +
                                             "\nError details: " + errorBody);
                }

                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to call OpenRouter API", e);
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
                // Build request with streaming enabled
                String requestBody = buildRequestBody(messages, options, true);

                // Make HTTP request
                String requestUrl = baseUrl + "/chat/completions";
                Request.Builder reqBuilder = new Request.Builder()
                    .url(requestUrl)
                    .post(RequestBody.create(requestBody, JSON));
                if (!isCustomBaseUrl()) {
                    reqBuilder.header("HTTP-Referer", "https://github.com/lightweightai/kernel");
                    reqBuilder.header("X-Title", "Lightweight AI Kernel");
                }
                if (!apiKey.isEmpty()) {
                    reqBuilder.header("Authorization", "Bearer " + apiKey);
                }
                Request request = reqBuilder.build();
                logger.info("OpenRouter streaming request: POST {} (model: {})", requestUrl, model);
                logger.debug("OpenRouter streaming request body: {}", requestBody);
                handler.onStart();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error details";
                        logger.error("OpenRouter streaming failed: POST {} → HTTP {} \nError: {}", requestUrl, response.code(), errorBody);
                        throw new RuntimeException("OpenRouter API call failed: " + response.code() +
                                                 " (url: " + requestUrl + ")" +
                                                 "\nError details: " + errorBody);
                    }

                    // Parse SSE stream
                    LLMResponse finalResponse = parseStreamingResponse(response.body(), handler);
                    handler.onComplete(finalResponse);
                    return finalResponse;
                }

            } catch (Exception e) {
                handler.onError(e);
                throw new RuntimeException("Failed to stream from OpenRouter API", e);
            }
        });
    }

    @Override
    public ModelCapability getModelCapability() {
        return modelCapability;
    }

    @Override
    public String getProviderName() {
        return "openrouter";
    }

    /**
     * Build request body for OpenRouter API (OpenAI-compatible format)
     */
    private String buildRequestBody(List<ConversationMessage> messages, LLMOptions options, boolean stream) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();

        // 自定义网关：api_key 放 body 里（部分自建网关要求 body 认证而非 Header）
        if (isCustomBaseUrl() && !apiKey.isEmpty()) {
            root.put("api_key", apiKey);
        }

        // Model
        root.put("model", model);

        // Max tokens
        int maxTokens = options != null && options.getMaxTokens() != null
            ? options.getMaxTokens()
            : 4096;
        root.put("max_tokens", maxTokens);

        // Temperature
        if (options != null && options.getTemperature() != null) {
            root.put("temperature", options.getTemperature());
        }

        // Streaming
        if (stream) {
            root.put("stream", true);
        }

        // Messages
        ArrayNode messagesArray = buildMessagesArray(messages);
        root.set("messages", messagesArray);

        // Tools (if provided) — convert from Claude format to OpenAI format
        if (options != null && options.getToolDefinitions() != null && !options.getToolDefinitions().isEmpty()) {
            ArrayNode toolsArray = objectMapper.createArrayNode();
            for (Map<String, Object> toolDef : options.getToolDefinitions()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode functionNode = objectMapper.createObjectNode();
                functionNode.put("name", (String) toolDef.get("name"));
                functionNode.put("description", (String) toolDef.get("description"));
                // "input_schema" (Claude format) → "parameters" (OpenAI format)
                Object schema = toolDef.getOrDefault("input_schema", toolDef.get("parameters"));
                if (schema != null) {
                    functionNode.set("parameters", objectMapper.valueToTree(schema));
                }
                toolNode.set("function", functionNode);
                toolsArray.add(toolNode);
            }
            root.set("tools", toolsArray);
        }

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Build messages array in OpenAI format.
     *
     * Handles three message types:
     *   1. Normal messages (system/user/assistant) — {"role": "...", "content": "..."}
     *   2. Assistant messages with tool calls — includes "tool_calls" array
     *   3. Tool result messages — includes "tool_call_id"
     */
    @SuppressWarnings("unchecked")
    private ArrayNode buildMessagesArray(List<ConversationMessage> messages) throws IOException {
        ArrayNode array = objectMapper.createArrayNode();

        for (ConversationMessage msg : messages) {
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", convertRole(msg.getRole()));

            Map<String, Object> metadata = msg.getMetadata();

            if (msg.getRole() == MessageRole.ASSISTANT && metadata.containsKey("tool_calls")) {
                // Assistant message with tool calls
                String text = msg.getTextContent();
                if (text != null && !text.isEmpty()) {
                    messageNode.put("content", text);
                } else {
                    messageNode.putNull("content");
                }
                // Serialize tool_calls in OpenAI format
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
                // Tool result message — must include tool_call_id
                messageNode.put("content", msg.getTextContent());
                if (metadata.containsKey("tool_use_id")) {
                    messageNode.put("tool_call_id", (String) metadata.get("tool_use_id"));
                }
            } else {
                // Normal message
                messageNode.put("content", msg.getTextContent());
            }

            array.add(messageNode);
        }

        return array;
    }

    /**
     * Convert our MessageRole to OpenAI role format
     */
    private String convertRole(MessageRole role) {
        switch (role) {
            case SYSTEM:
                return "system";
            case USER:
                return "user";
            case ASSISTANT:
                return "assistant";
            case TOOL:
                return "tool";
            default:
                throw new IllegalArgumentException("Unsupported role: " + role);
        }
    }

    /**
     * Parse OpenRouter API response (OpenAI format)
     *
     * Handles two cases:
     *   1. Normal text response — content is a string
     *   2. Tool call response  — content is null, tool_calls array is present
     */
    private LLMResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        // Extract message from choices
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
            throw new RuntimeException("No choices in response");
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.get("message");

        // content may be null when finish_reason == "tool_calls"
        String content = "";
        JsonNode contentNode = message.get("content");
        if (contentNode != null && !contentNode.isNull()) {
            content = contentNode.asText();
        }

        // Parse tool_calls (OpenAI function-calling format)
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
            if (!toolCalls.isEmpty()) {
                logger.debug("tool_calls detected: {}", toolCalls.size());
            }
        }

        // Create conversation message
        ConversationMessage responseMessage = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent(content)
            .build();

        // Extract usage info
        JsonNode usage = root.get("usage");
        LLMResponse.UsageInfo usageInfo = null;
        if (usage != null) {
            int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
            usageInfo = new LLMResponse.UsageInfo(promptTokens, completionTokens);
        }

        // Extract stop reason
        String finishReason = firstChoice.has("finish_reason") ? firstChoice.get("finish_reason").asText() : null;

        // Build response
        return LLMResponse.builder()
            .message(responseMessage)
            .toolCalls(toolCalls)
            .stopReason(finishReason)
            .usage(usageInfo)
            .build();
    }

    /**
     * Parse streaming response using Server-Sent Events.
     *
     * Handles both text responses and tool_call responses (OpenAI function-calling format).
     * Tool call arguments are streamed in chunks and assembled here.
     */
    private LLMResponse parseStreamingResponse(ResponseBody responseBody, StreamEventHandler handler) throws IOException {
        StringBuilder textContent = new StringBuilder();
        String finishReason = null;

        // Tool call chunks are keyed by their index
        Map<Integer, ToolCallAccumulator> toolCallMap = new LinkedHashMap<>();

        // Parse SSE events line by line
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(responseBody.byteStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();

                    if (data.equals("[DONE]")) {
                        break;
                    }

                    try {
                        JsonNode eventData = objectMapper.readTree(data);
                        JsonNode choices = eventData.get("choices");

                        if (choices != null && choices.isArray() && choices.size() > 0) {
                            JsonNode firstChoice = choices.get(0);
                            JsonNode delta = firstChoice.get("delta");

                            if (delta != null) {
                                // Text content — skip empty chunks from API warmup
                                if (delta.has("content") && !delta.get("content").isNull()) {
                                    String deltaContent = delta.get("content").asText();
                                    if (!deltaContent.isEmpty()) {
                                        textContent.append(deltaContent);
                                        handler.onTextDelta(deltaContent);
                                    }
                                }

                                // Tool call chunks (OpenAI streaming format)
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

                            if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isNull()) {
                                finishReason = firstChoice.get("finish_reason").asText();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors for individual events
                    }
                }
            }
        }

        // Assemble tool calls from accumulated chunks
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
                logger.warn("Failed to parse streaming tool call args: {}", e.getMessage());
            }
        }
        if (!toolCalls.isEmpty()) {
            logger.debug("Streaming tool_calls assembled: {}", toolCalls.size());
        }

        // Build final response
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

    /** 是否使用了自定义 base URL（非默认 OpenRouter） */
    private boolean isCustomBaseUrl() {
        return !DEFAULT_BASE_URL.equals(baseUrl);
    }

    /** Accumulates streamed tool call chunks by index */
    private static class ToolCallAccumulator {
        String id = "";
        String name = "";
        StringBuilder argsJson = new StringBuilder();
    }
}
