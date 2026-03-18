package com.lightweightai.kernel.llm.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claude Pro Provider - Uses Claude Pro session for API access
 *
 * This provider connects to claude.ai using a session key from Claude Pro subscription.
 * Note: This is unofficial and may break if Claude changes their API.
 */
public class ClaudeProProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeProProvider.class);
    private static final String BOOTSTRAP_URL = "https://claude.ai/api/bootstrap";
    private static final String CREATE_CHAT_URL = "https://claude.ai/api/organizations/%s/chat_conversations";
    private static final String APPEND_MESSAGE_URL = "https://claude.ai/api/append_message";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String sessionKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelCapability modelCapability;

    private String organizationId;
    private String conversationId;

    /**
     * Create a new ClaudeProProvider
     *
     * @param sessionKey Session key from Claude Pro
     * @param organizationId Organization ID (can be null, will be fetched automatically)
     */
    public ClaudeProProvider(String sessionKey, String organizationId) {
        if (sessionKey == null || sessionKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Session key cannot be null or empty");
        }

        this.sessionKey = sessionKey;
        this.organizationId = organizationId;
        this.httpClient = new OkHttpClient.Builder()
            .build();
        this.objectMapper = new ObjectMapper();
        this.modelCapability = new ClaudeModelCapability("claude-3-5-sonnet-20241022");

        // Try to initialize organization ID if not provided
        if (this.organizationId == null || this.organizationId.trim().isEmpty()) {
            try {
                fetchOrganizationId();
            } catch (Exception e) {
                logger.warn("Failed to fetch organization ID: {}", e.getMessage());
            }
        }
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        try {
            // Ensure we have organization ID
            if (organizationId == null || organizationId.trim().isEmpty()) {
                fetchOrganizationId();
            }

            // Create conversation if needed
            if (conversationId == null) {
                conversationId = createConversation();
            }

            // Get the last user message
            String prompt = extractLastUserMessage(messages);

            // Build request
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("organization_uuid", organizationId);
            requestBody.put("conversation_uuid", conversationId);
            requestBody.put("text", prompt);
            requestBody.put("timezone", "Asia/Shanghai");

            // Attachments and files (empty for now)
            requestBody.set("attachments", objectMapper.createArrayNode());
            requestBody.set("files", objectMapper.createArrayNode());

            // Make HTTP request
            Request request = new Request.Builder()
                .url(APPEND_MESSAGE_URL)
                .header("Cookie", "sessionKey=" + sessionKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    throw new RuntimeException("Claude Pro API call failed: " + response.code() +
                                             "\nError: " + errorBody +
                                             "\nTip: Session key may have expired. Run 'python3 get-session-key.py' to get a new one.");
                }

                String responseBody = response.body().string();
                return parseStreamingResponse(responseBody);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to call Claude Pro API: " + e.getMessage(), e);
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
            handler.onStart();
            LLMResponse response = complete(messages, options);

            // Simulate streaming
            String text = response.getMessage().getTextContent();
            for (int i = 0; i < text.length(); i += 10) {
                int end = Math.min(i + 10, text.length());
                handler.onTextDelta(text.substring(i, end));
            }

            handler.onComplete(response);
            return response;
        });
    }

    @Override
    public ModelCapability getModelCapability() {
        return modelCapability;
    }

    @Override
    public String getProviderName() {
        return "claude-pro";
    }

    /**
     * Fetch organization ID from bootstrap API
     */
    private void fetchOrganizationId() throws IOException {
        Request request = new Request.Builder()
            .url(BOOTSTRAP_URL)
            .header("Cookie", "sessionKey=" + sessionKey)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to fetch organization ID: " + response.code() +
                                         "\nSession key may be invalid or expired.");
            }

            JsonNode root = objectMapper.readTree(response.body().string());

            // Get first organization from account
            if (root.has("account") && root.get("account").has("memberships")) {
                JsonNode memberships = root.get("account").get("memberships");
                if (memberships.isArray() && memberships.size() > 0) {
                    JsonNode firstMembership = memberships.get(0);
                    if (firstMembership.has("organization")) {
                        this.organizationId = firstMembership.get("organization").get("uuid").asText();
                        logger.info("Fetched organization ID: {}", organizationId);
                        return;
                    }
                }
            }

            throw new RuntimeException("No organization found in account. Please check your Claude Pro subscription.");
        }
    }

    /**
     * Create a new conversation
     */
    private String createConversation() throws IOException {
        String url = String.format(CREATE_CHAT_URL, organizationId);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("uuid", UUID.randomUUID().toString());
        body.put("name", "");

        Request request = new Request.Builder()
            .url(url)
            .header("Cookie", "sessionKey=" + sessionKey)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("Failed to create conversation: " + response.code() + "\n" + errorBody);
            }

            JsonNode responseData = objectMapper.readTree(response.body().string());
            String uuid = responseData.get("uuid").asText();
            logger.info("Created conversation: {}", uuid);
            return uuid;
        }
    }

    /**
     * Extract last user message from conversation
     */
    private String extractLastUserMessage(List<ConversationMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == MessageRole.USER) {
                return messages.get(i).getTextContent();
            }
        }
        return "";
    }

    /**
     * Parse streaming response from Claude Pro API
     * The response is a series of SSE events
     */
    private LLMResponse parseStreamingResponse(String responseBody) {
        StringBuilder completionText = new StringBuilder();

        // Parse SSE format response
        String[] lines = responseBody.split("\n");
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                try {
                    String data = line.substring(6).trim();
                    if (data.isEmpty() || data.equals("[DONE]")) {
                        continue;
                    }

                    JsonNode eventData = objectMapper.readTree(data);

                    // Extract completion text from different event types
                    if (eventData.has("completion")) {
                        completionText.append(eventData.get("completion").asText());
                    } else if (eventData.has("delta") && eventData.get("delta").has("text")) {
                        completionText.append(eventData.get("delta").get("text").asText());
                    }
                } catch (Exception e) {
                    // Ignore parsing errors for individual events
                }
            }
        }

        // Create conversation message
        ConversationMessage message = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent(completionText.toString())
            .build();

        return LLMResponse.builder()
            .message(message)
            .build();
    }
}
