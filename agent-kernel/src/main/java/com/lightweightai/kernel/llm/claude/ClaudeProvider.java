package com.lightweightai.kernel.llm.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import okhttp3.*;

import java.io.IOException;
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
                // Build request with streaming enabled
                String requestBody = buildRequestBody(messages, options, true);

                // Make HTTP request
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

                    // Parse SSE stream
                    LLMResponse finalResponse = parseStreamingResponse(response.body(), handler);
                    handler.onComplete(finalResponse);
                    return finalResponse;
                }

            } catch (Exception e) {
                handler.onError(e);
                throw new RuntimeException("Failed to stream from Claude API", e);
            }
        });
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
        return buildRequestBody(messages, options, false);
    }

    /**
     * Build request body for Claude API with optional streaming
     */
    private String buildRequestBody(List<ConversationMessage> messages, LLMOptions options, boolean stream) throws IOException {
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

        // Streaming
        if (stream) {
            root.put("stream", true);
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

    /**
     * Parse streaming response using Server-Sent Events
     */
    private LLMResponse parseStreamingResponse(ResponseBody responseBody, StreamEventHandler handler) throws IOException {
        StringBuilder textContent = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        String stopReason = null;
        LLMResponse.UsageInfo usageInfo = null;

        // For tracking tool use blocks being built
        Map<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();

        // Parse SSE events line by line
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(responseBody.byteStream()))) {

            String line;
            String eventType = null;
            StringBuilder dataBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event: ")) {
                    // New event type
                    eventType = line.substring(7).trim();
                } else if (line.startsWith("data: ")) {
                    // Event data
                    dataBuilder.append(line.substring(6));
                } else if (line.isEmpty() && eventType != null) {
                    // End of event - process it
                    String data = dataBuilder.toString();
                    if (!data.isEmpty()) {
                        processStreamEvent(eventType, data, textContent, toolCalls,
                                         toolCallBuilders, handler);
                    }

                    // Reset for next event
                    eventType = null;
                    dataBuilder = new StringBuilder();
                }
            }
        }

        // Build final response
        ConversationMessage message = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent(textContent.toString())
            .build();

        return LLMResponse.builder()
            .message(message)
            .toolCalls(toolCalls)
            .stopReason(stopReason)
            .usage(usageInfo)
            .build();
    }

    /**
     * Process a single SSE event
     */
    private void processStreamEvent(
        String eventType,
        String data,
        StringBuilder textContent,
        List<ToolCall> toolCalls,
        Map<Integer, ToolCallBuilder> toolCallBuilders,
        StreamEventHandler handler
    ) throws IOException {
        JsonNode eventData = objectMapper.readTree(data);

        switch (eventType) {
            case "message_start":
                // Message started - nothing to do
                break;

            case "content_block_start":
                // New content block starting
                int index = eventData.get("index").asInt();
                JsonNode contentBlock = eventData.get("content_block");
                String type = contentBlock.get("type").asText();

                if ("tool_use".equals(type)) {
                    // Start building a tool call
                    String id = contentBlock.get("id").asText();
                    String name = contentBlock.get("name").asText();
                    toolCallBuilders.put(index, new ToolCallBuilder(id, name));
                }
                break;

            case "content_block_delta":
                // Content delta
                int deltaIndex = eventData.get("index").asInt();
                JsonNode delta = eventData.get("delta");
                String deltaType = delta.get("type").asText();

                if ("text_delta".equals(deltaType)) {
                    // Text content
                    String text = delta.get("text").asText();
                    textContent.append(text);
                    handler.onTextDelta(text);
                } else if ("input_json_delta".equals(deltaType)) {
                    // Tool call input delta
                    String partialJson = delta.get("partial_json").asText();
                    ToolCallBuilder builder = toolCallBuilders.get(deltaIndex);
                    if (builder != null) {
                        builder.appendInput(partialJson);
                    }
                }
                break;

            case "content_block_stop":
                // Content block finished
                int stopIndex = eventData.get("index").asInt();
                ToolCallBuilder builder = toolCallBuilders.get(stopIndex);
                if (builder != null) {
                    // Finalize tool call
                    ToolCall toolCall = builder.build(objectMapper);
                    toolCalls.add(toolCall);
                    handler.onToolCallDelta(toolCall);
                    toolCallBuilders.remove(stopIndex);
                }
                break;

            case "message_delta":
                // Message metadata delta (usually stop_reason)
                JsonNode messageDelta = eventData.get("delta");
                if (messageDelta.has("stop_reason")) {
                    // Stop reason updated
                }
                break;

            case "message_stop":
                // Message complete
                break;

            default:
                // Unknown event type - ignore
                break;
        }
    }

    /**
     * Helper class for building tool calls incrementally
     */
    private static class ToolCallBuilder {
        private final String id;
        private final String name;
        private final StringBuilder inputJson = new StringBuilder();

        public ToolCallBuilder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public void appendInput(String partialJson) {
            inputJson.append(partialJson);
        }

        public ToolCall build(ObjectMapper mapper) throws IOException {
            Map<String, Object> arguments = mapper.readValue(
                inputJson.toString(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            return new ToolCall(id, name, arguments);
        }
    }
}
