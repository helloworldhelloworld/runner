package com.lightweightai.kernel.llm.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenRouter Provider - Unified API for multiple LLM models
 *
 * OpenRouter provides access to Claude, GPT-4, and other models through a single API.
 * API is compatible with OpenAI format.
 */
public class OpenRouterProvider implements LLMProvider {

    private static final String API_BASE_URL = "https://openrouter.ai/api/v1";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelCapability modelCapability;

    /**
     * Create a new OpenRouterProvider
     *
     * @param apiKey OpenRouter API key
     * @param model Model name (e.g., "anthropic/claude-3.5-sonnet")
     */
    public OpenRouterProvider(String apiKey, String model) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.model = model;
        // 使用系统默认设置，添加超时
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
        this.modelCapability = new OpenRouterModelCapability(model);

        // 调试日志
        System.out.println("[OpenRouterProvider] Initialized with model: " + model);
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        try {
            // Build request
            String requestBody = buildRequestBody(messages, options, false);
            System.out.println("[OpenRouterProvider] Calling API with " + messages.size() + " messages");

            // Make HTTP request
            Request request = new Request.Builder()
                .url(API_BASE_URL + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://github.com/lightweightai/kernel")
                .header("X-Title", "Lightweight AI Kernel")
                .post(RequestBody.create(requestBody, JSON))
                .build();

            System.out.println("[OpenRouterProvider] Making HTTP request...");
            try (Response response = httpClient.newCall(request).execute()) {
                System.out.println("[OpenRouterProvider] Got response: " + response.code());
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    throw new RuntimeException("OpenRouter API call failed: " + response.code() +
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
                Request request = new Request.Builder()
                    .url(API_BASE_URL + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://github.com/lightweightai/kernel")
                    .header("X-Title", "Lightweight AI Kernel")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();

                handler.onStart();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new RuntimeException("OpenRouter API call failed: " + response.code());
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

        // Tools (if provided)
        if (options != null && options.getToolDefinitions() != null && !options.getToolDefinitions().isEmpty()) {
            ArrayNode toolsArray = objectMapper.valueToTree(options.getToolDefinitions());
            root.set("tools", toolsArray);
        }

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Build messages array in OpenAI format
     */
    private ArrayNode buildMessagesArray(List<ConversationMessage> messages) {
        ArrayNode array = objectMapper.createArrayNode();

        for (ConversationMessage msg : messages) {
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", convertRole(msg.getRole()));
            messageNode.put("content", msg.getTextContent());
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

        String content = message.get("content").asText();

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
            .stopReason(finishReason)
            .usage(usageInfo)
            .build();
    }

    /**
     * Parse streaming response using Server-Sent Events
     */
    private LLMResponse parseStreamingResponse(ResponseBody responseBody, StreamEventHandler handler) throws IOException {
        StringBuilder textContent = new StringBuilder();
        String finishReason = null;

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

                            if (delta != null && delta.has("content")) {
                                String deltaContent = delta.get("content").asText();
                                textContent.append(deltaContent);
                                handler.onTextDelta(deltaContent);
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

        // Build final response
        ConversationMessage message = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent(textContent.toString())
            .build();

        return LLMResponse.builder()
            .message(message)
            .stopReason(finishReason)
            .build();
    }
}
