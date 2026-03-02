package com.lightweightai.kernel.llm.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Claude API Provider Implementation
 *
 * Supports Claude 3.5 Sonnet and other Claude models via Anthropic API
 */
public class ClaudeProvider implements LLMProvider {

    private static final String API_BASE_URL = "https://api.anthropic.com/v1";
    private static final String API_VERSION = "2023-06-01";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelCapability modelCapability;

    /**
     * Create a new ClaudeProvider
     *
     * @param apiKey Anthropic API key
     * @param model Model name (e.g., "claude-3-5-sonnet-20241022")
     */
    public ClaudeProvider(String apiKey, String model) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
            .build();
        this.objectMapper = new ObjectMapper();
        this.modelCapability = new ClaudeModelCapability(model);
    }

    /**
     * Create with custom OkHttpClient
     */
    public ClaudeProvider(String apiKey, String model, OkHttpClient httpClient) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.modelCapability = new ClaudeModelCapability(model);
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        try {
            // Build request
            String requestBody = buildRequestBody(messages, options);

            // Make HTTP request
            Request request = new Request.Builder()
                .url(API_BASE_URL + "/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .post(RequestBody.create(requestBody, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("API call failed: " + response.code() + " " + response.message());
                }

                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to call Claude API", e);
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
                // Build request with stream: true
                String requestBody = buildStreamRequestBody(messages, options);

                Request request = new Request.Builder()
                    .url(API_BASE_URL + "/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();

                handler.onStart();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new RuntimeException("API call failed: " + response.code() + " " + response.message());
                    }

                    LLMResponse result = processStreamResponse(response, handler);
                    handler.onComplete(result);
                    return result;
                }

            } catch (Exception e) {
                handler.onError(e);
                throw new RuntimeException("Streaming failed", e);
            }
        });
    }

    /**
     * Build request body with stream: true
     */
    private String buildStreamRequestBody(List<ConversationMessage> messages, LLMOptions options) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("stream", true);

        int maxTokens = options != null && options.getMaxTokens() != null
            ? options.getMaxTokens()
            : 4096;
        root.put("max_tokens", maxTokens);

        if (options != null && options.getTemperature() != null) {
            root.put("temperature", options.getTemperature());
        }

        String systemPrompt = extractSystemPrompt(messages);
        if (systemPrompt != null) {
            root.put("system", systemPrompt);
        }

        root.set("messages", buildMessagesArray(messages));

        if (options != null && options.getToolDefinitions() != null && !options.getToolDefinitions().isEmpty()) {
            ArrayNode toolsArray = objectMapper.valueToTree(options.getToolDefinitions());
            root.set("tools", toolsArray);
        }

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Process SSE stream response from Claude API
     *
     * Claude SSE event types:
     *   message_start       → message metadata
     *   content_block_start → new content block (text or tool_use)
     *   content_block_delta → incremental content (text_delta or input_json_delta)
     *   content_block_stop  → content block complete
     *   message_delta       → stop_reason, usage
     *   message_stop        → stream finished
     */
    private LLMResponse processStreamResponse(Response response, StreamEventHandler handler) throws IOException {
        StringBuilder fullText = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        String stopReason = null;
        LLMResponse.UsageInfo usageInfo = null;

        // Track current tool_use block being streamed
        String currentToolId = null;
        String currentToolName = null;
        StringBuilder currentToolJson = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // SSE format: "event: <type>\ndata: <json>\n\n"
                if (!line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6).trim();
                if (data.isEmpty()) {
                    continue;
                }

                JsonNode event = objectMapper.readTree(data);
                String type = event.has("type") ? event.get("type").asText() : "";

                switch (type) {
                    case "content_block_start": {
                        JsonNode block = event.get("content_block");
                        if (block != null && "tool_use".equals(block.path("type").asText())) {
                            currentToolId = block.get("id").asText();
                            currentToolName = block.get("name").asText();
                            currentToolJson.setLength(0);
                        }
                        break;
                    }

                    case "content_block_delta": {
                        JsonNode delta = event.get("delta");
                        if (delta == null) break;

                        String deltaType = delta.path("type").asText();

                        if ("text_delta".equals(deltaType)) {
                            String text = delta.get("text").asText();
                            fullText.append(text);
                            handler.onTextDelta(text);
                        } else if ("input_json_delta".equals(deltaType)) {
                            String partialJson = delta.get("partial_json").asText();
                            currentToolJson.append(partialJson);
                        }
                        break;
                    }

                    case "content_block_stop": {
                        // If we were building a tool call, finalize it
                        if (currentToolId != null) {
                            Map<String, Object> args = Map.of();
                            String jsonStr = currentToolJson.toString().trim();
                            if (!jsonStr.isEmpty()) {
                                try {
                                    args = objectMapper.readValue(jsonStr, Map.class);
                                } catch (Exception e) {
                                    // partial JSON parse failure
                                    args = Map.of();
                                }
                            }
                            ToolCall toolCall = new ToolCall(currentToolId, currentToolName, args);
                            toolCalls.add(toolCall);
                            handler.onToolCallDelta(toolCall);

                            currentToolId = null;
                            currentToolName = null;
                            currentToolJson.setLength(0);
                        }
                        break;
                    }

                    case "message_delta": {
                        JsonNode delta = event.get("delta");
                        if (delta != null && delta.has("stop_reason")) {
                            stopReason = delta.get("stop_reason").asText();
                        }
                        JsonNode usage = event.get("usage");
                        if (usage != null && usage.has("output_tokens")) {
                            int outputTokens = usage.get("output_tokens").asInt();
                            usageInfo = new LLMResponse.UsageInfo(0, outputTokens);
                        }
                        break;
                    }

                    // message_start, message_stop: no action needed
                    default:
                        break;
                }
            }
        }

        ConversationMessage message = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent(fullText.toString())
            .build();

        return LLMResponse.builder()
            .message(message)
            .toolCalls(toolCalls)
            .stopReason(stopReason)
            .usage(usageInfo)
            .build();
    }

    @Override
    public ModelCapability getModelCapability() {
        return modelCapability;
    }

    @Override
    public String getProviderName() {
        return "claude";
    }

    /**
     * Build request body for Claude API
     */
    private String buildRequestBody(List<ConversationMessage> messages, LLMOptions options) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();

        // Model
        root.put("model", model);

        // Max tokens (required by Claude API)
        int maxTokens = options != null && options.getMaxTokens() != null
            ? options.getMaxTokens()
            : 4096;
        root.put("max_tokens", maxTokens);

        // Temperature
        if (options != null && options.getTemperature() != null) {
            root.put("temperature", options.getTemperature());
        }

        // System prompt (separate from messages in Claude API)
        String systemPrompt = extractSystemPrompt(messages);
        if (systemPrompt != null) {
            root.put("system", systemPrompt);
        }

        // Messages (excluding system messages)
        ArrayNode messagesArray = buildMessagesArray(messages);
        root.set("messages", messagesArray);

        // Tools
        if (options != null && options.getToolDefinitions() != null && !options.getToolDefinitions().isEmpty()) {
            ArrayNode toolsArray = objectMapper.valueToTree(options.getToolDefinitions());
            root.set("tools", toolsArray);
        }

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Extract system prompt from messages
     */
    private String extractSystemPrompt(List<ConversationMessage> messages) {
        for (ConversationMessage msg : messages) {
            if (msg.getRole() == MessageRole.SYSTEM) {
                return msg.getTextContent();
            }
        }
        return null;
    }

    /**
     * Build messages array for Claude API (excluding system messages)
     */
    private ArrayNode buildMessagesArray(List<ConversationMessage> messages) {
        ArrayNode array = objectMapper.createArrayNode();

        for (ConversationMessage msg : messages) {
            // Skip system messages (handled separately)
            if (msg.getRole() == MessageRole.SYSTEM) {
                continue;
            }

            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", convertRole(msg.getRole()));

            // Content
            messageNode.put("content", msg.getTextContent());

            array.add(messageNode);
        }

        return array;
    }

    /**
     * Convert our MessageRole to Claude's role format
     */
    private String convertRole(MessageRole role) {
        switch (role) {
            case USER:
            case TOOL:  // Tool results are sent as user messages
                return "user";
            case ASSISTANT:
                return "assistant";
            default:
                throw new IllegalArgumentException("Unsupported role: " + role);
        }
    }

    /**
     * Parse Claude API response
     */
    private LLMResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        // Extract content
        JsonNode contentArray = root.get("content");
        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder textContent = new StringBuilder();

        if (contentArray != null && contentArray.isArray()) {
            for (JsonNode contentBlock : contentArray) {
                String type = contentBlock.get("type").asText();

                if ("text".equals(type)) {
                    textContent.append(contentBlock.get("text").asText());
                } else if ("tool_use".equals(type)) {
                    // Parse tool use
                    String id = contentBlock.get("id").asText();
                    String name = contentBlock.get("name").asText();
                    JsonNode input = contentBlock.get("input");

                    Map<String, Object> arguments = objectMapper.convertValue(input, Map.class);
                    toolCalls.add(new ToolCall(id, name, arguments));
                }
            }
        }

        // Create conversation message
        ConversationMessage message = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent(textContent.toString())
            .build();

        // Extract usage info
        JsonNode usage = root.get("usage");
        LLMResponse.UsageInfo usageInfo = null;
        if (usage != null) {
            int inputTokens = usage.get("input_tokens").asInt();
            int outputTokens = usage.get("output_tokens").asInt();
            usageInfo = new LLMResponse.UsageInfo(inputTokens, outputTokens);
        }

        // Extract stop reason
        String stopReason = root.has("stop_reason") ? root.get("stop_reason").asText() : null;

        // Build response
        return LLMResponse.builder()
            .message(message)
            .toolCalls(toolCalls)
            .stopReason(stopReason)
            .usage(usageInfo)
            .build();
    }
}
