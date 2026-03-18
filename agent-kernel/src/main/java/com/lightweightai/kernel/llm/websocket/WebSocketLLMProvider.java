package com.lightweightai.kernel.llm.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.MessageFormatter;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.TokenCounter;
import com.lightweightai.kernel.llm.ToolCall;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket-based LLM Provider using OkHttp
 *
 * Features:
 * - Bidirectional real-time communication
 * - Streaming text responses
 * - Function calling (Claude Skills) support
 * - Automatic reconnection
 * - Heartbeat/ping-pong
 * - Async non-blocking
 *
 * Based on OkHttp WebSocket - lightweight and battle-tested
 */
public class WebSocketLLMProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketLLMProvider.class);

    private final String websocketUrl;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelCapability modelCapability;

    private WebSocket webSocket;
    private final AtomicLong requestIdCounter = new AtomicLong(0);
    private final Map<String, CompletableFuture<LLMResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, StreamEventHandler> streamHandlers = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> streamTextBuffers = new ConcurrentHashMap<>();
    private final Map<String, List<ToolCall>> streamToolCalls = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeatTask;

    private volatile boolean connected = false;
    private final int heartbeatInterval = 30; // seconds
    private final int reconnectDelay = 5; // seconds

    public WebSocketLLMProvider(String websocketUrl, String model) {
        this(websocketUrl, model, new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for long-running connections
            .writeTimeout(10, TimeUnit.SECONDS)
            .build());
    }

    public WebSocketLLMProvider(String websocketUrl, String model, OkHttpClient httpClient) {
        this.websocketUrl = websocketUrl;
        this.model = model;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.modelCapability = new WebSocketModelCapability(model);
    }

    /**
     * Connect to WebSocket server
     */
    public synchronized CompletableFuture<Void> connect() {
        if (connected && webSocket != null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> connectFuture = new CompletableFuture<>();

        Request request = new Request.Builder()
            .url(websocketUrl)
            .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                logger.info("WebSocket connected: {}", websocketUrl);
                connected = true;
                startHeartbeat();
                connectFuture.complete(null);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                handleMessage(bytes.utf8());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                logger.info("WebSocket closing: {} {}", code, reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.info("WebSocket closed: {} {}", code, reason);
                handleDisconnection();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("WebSocket failure: {}", t.getMessage());
                connectFuture.completeExceptionally(t);
                handleDisconnection();

                // Auto-reconnect after delay
                scheduleReconnect();
            }
        });

        return connectFuture;
    }

    /**
     * Disconnect from WebSocket server
     */
    public synchronized void disconnect() {
        if (!connected) {
            return;
        }

        stopHeartbeat();

        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
        }

        connected = false;
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected && webSocket != null;
    }

    /**
     * Handle disconnection
     */
    private void handleDisconnection() {
        connected = false;
        stopHeartbeat();

        // Fail all pending requests
        RuntimeException error = new RuntimeException("WebSocket connection closed");
        pendingRequests.values().forEach(future ->
            future.completeExceptionally(error)
        );
        pendingRequests.clear();

        // Notify stream handlers
        streamHandlers.values().forEach(handler ->
            handler.onError(error)
        );
        streamHandlers.clear();
        streamTextBuffers.clear();
        streamToolCalls.clear();
    }

    /**
     * Schedule reconnection
     */
    private void scheduleReconnect() {
        heartbeatExecutor.schedule(() -> {
            logger.info("Attempting to reconnect...");
            try {
                connect().get();
            } catch (Exception e) {
                logger.error("Reconnection failed: {}", e.getMessage());
            }
        }, reconnectDelay, TimeUnit.SECONDS);
    }

    /**
     * Handle incoming WebSocket messages
     */
    private void handleMessage(String text) {
        try {
            WebSocketMessage message = WebSocketMessage.fromJson(text);
            String requestId = message.getRequestId();

            switch (message.getType()) {
                case CHAT_RESPONSE:
                    handleChatResponse(requestId, message);
                    break;

                case TEXT_DELTA:
                    handleTextDelta(requestId, message);
                    break;

                case TOOL_CALL:
                    handleToolCall(requestId, message);
                    break;

                case ERROR:
                    handleError(requestId, message);
                    break;

                case PONG:
                    // Heartbeat response, connection is alive
                    break;

                default:
                    logger.warn("Unknown message type: {}", message.getType());
            }
        } catch (Exception e) {
            logger.error("Failed to parse WebSocket message: {}", e.getMessage());
        }
    }

    private void handleChatResponse(String requestId, WebSocketMessage message) {
        CompletableFuture<LLMResponse> future = pendingRequests.remove(requestId);
        if (future == null) {
            // Might be streaming response
            completeStreamingResponse(requestId);
            return;
        }

        try {
            WebSocketMessage.ChatResponseData data = objectMapper.convertValue(
                message.getData(),
                WebSocketMessage.ChatResponseData.class
            );

            LLMResponse response = buildLLMResponse(data);
            future.complete(response);

        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    private void handleTextDelta(String requestId, WebSocketMessage message) {
        StreamEventHandler handler = streamHandlers.get(requestId);
        if (handler == null) {
            return;
        }

        try {
            WebSocketMessage.TextDeltaData data = objectMapper.convertValue(
                message.getData(),
                WebSocketMessage.TextDeltaData.class
            );

            String text = data.getText();
            handler.onTextDelta(text);

            // Accumulate text
            streamTextBuffers.computeIfAbsent(requestId, k -> new StringBuilder()).append(text);

        } catch (Exception e) {
            handler.onError(e);
        }
    }

    private void handleToolCall(String requestId, WebSocketMessage message) {
        StreamEventHandler handler = streamHandlers.get(requestId);
        if (handler == null) {
            return;
        }

        try {
            WebSocketMessage.ToolCallData data = objectMapper.convertValue(
                message.getData(),
                WebSocketMessage.ToolCallData.class
            );

            ToolCall toolCall = new ToolCall(data.getId(), data.getName(), data.getInput());
            handler.onToolCallDelta(toolCall);

            // Accumulate tool calls
            streamToolCalls.computeIfAbsent(requestId, k -> new ArrayList<>()).add(toolCall);

        } catch (Exception e) {
            handler.onError(e);
        }
    }

    private void handleError(String requestId, WebSocketMessage message) {
        RuntimeException error = new RuntimeException(message.getError());

        CompletableFuture<LLMResponse> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.completeExceptionally(error);
        }

        StreamEventHandler handler = streamHandlers.remove(requestId);
        if (handler != null) {
            handler.onError(error);
            streamTextBuffers.remove(requestId);
            streamToolCalls.remove(requestId);
        }
    }

    /**
     * Complete streaming response
     */
    private void completeStreamingResponse(String requestId) {
        StreamEventHandler handler = streamHandlers.remove(requestId);
        if (handler == null) {
            return;
        }

        StringBuilder textBuffer = streamTextBuffers.remove(requestId);
        List<ToolCall> toolCalls = streamToolCalls.remove(requestId);

        LLMResponse finalResponse = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(MessageRole.ASSISTANT)
                .textContent(textBuffer != null ? textBuffer.toString() : "")
                .build())
            .toolCalls(toolCalls != null ? toolCalls : List.of())
            .build();

        handler.onComplete(finalResponse);
    }

    /**
     * Send message to WebSocket server
     */
    private CompletableFuture<Void> sendMessage(WebSocketMessage message) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(
                new IOException("Not connected to WebSocket server")
            );
        }

        try {
            String json = message.toJson();
            webSocket.send(json);
            return CompletableFuture.completedFuture(null);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Start heartbeat to keep connection alive
     */
    private void startHeartbeat() {
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (isConnected()) {
                    String requestId = "ping_" + System.currentTimeMillis();
                    sendMessage(WebSocketMessage.ping(requestId));
                }
            } catch (Exception e) {
                logger.warn("Heartbeat failed: {}", e.getMessage());
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.SECONDS);
    }

    /**
     * Stop heartbeat
     */
    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    // LLMProvider interface implementation

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        try {
            return completeAsync(messages, options).get();
        } catch (Exception e) {
            throw new RuntimeException("WebSocket LLM call failed", e);
        }
    }

    @Override
    public CompletableFuture<LLMResponse> completeAsync(
        List<ConversationMessage> messages,
        LLMOptions options
    ) {
        String requestId = "req_" + requestIdCounter.incrementAndGet();
        CompletableFuture<LLMResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        CompletableFuture<Void> sendFuture = ensureConnected()
            .thenCompose(v -> {
                // Build and send request
                WebSocketMessage.ChatRequestData requestData = buildChatRequest(messages, options, false);
                WebSocketMessage request = WebSocketMessage.chatRequest(requestId, requestData);
                return sendMessage(request);
            });

        sendFuture.exceptionally(error -> {
            pendingRequests.remove(requestId);
            future.completeExceptionally(error);
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<LLMResponse> completeStream(
        List<ConversationMessage> messages,
        LLMOptions options,
        StreamEventHandler handler
    ) {
        String requestId = "stream_" + requestIdCounter.incrementAndGet();
        CompletableFuture<LLMResponse> future = new CompletableFuture<>();

        streamHandlers.put(requestId, handler);
        streamTextBuffers.put(requestId, new StringBuilder());
        streamToolCalls.put(requestId, new ArrayList<>());

        handler.onStart();

        CompletableFuture<Void> sendFuture = ensureConnected()
            .thenCompose(v -> {
                // Build and send streaming request
                WebSocketMessage.ChatRequestData requestData = buildChatRequest(messages, options, true);
                WebSocketMessage request = WebSocketMessage.chatRequest(requestId, requestData);
                return sendMessage(request);
            });

        sendFuture.exceptionally(error -> {
            streamHandlers.remove(requestId);
            streamTextBuffers.remove(requestId);
            streamToolCalls.remove(requestId);
            handler.onError(error);
            future.completeExceptionally(error);
            return null;
        });

        return future;
    }

    /**
     * Ensure connected, reconnect if needed
     */
    private CompletableFuture<Void> ensureConnected() {
        if (isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        return connect();
    }

    private WebSocketMessage.ChatRequestData buildChatRequest(
        List<ConversationMessage> messages,
        LLMOptions options,
        boolean stream
    ) {
        WebSocketMessage.ChatRequestData requestData = new WebSocketMessage.ChatRequestData();

        // Convert messages
        List<Map<String, Object>> messagesList = new ArrayList<>();
        for (ConversationMessage msg : messages) {
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("role", convertRole(msg.getRole()));
            msgMap.put("content", msg.getTextContent());
            messagesList.add(msgMap);
        }
        requestData.setMessages(messagesList);

        // Add tools
        if (options != null && options.getToolDefinitions() != null) {
            requestData.setTools(options.getToolDefinitions());
        }

        // Options
        if (options != null) {
            requestData.setMaxTokens(options.getMaxTokens());
            requestData.setTemperature(options.getTemperature());
        }

        requestData.setStream(stream);

        return requestData;
    }

    private String convertRole(MessageRole role) {
        switch (role) {
            case USER:
            case TOOL:
                return "user";
            case ASSISTANT:
                return "assistant";
            case SYSTEM:
                return "system";
            default:
                return "user";
        }
    }

    private LLMResponse buildLLMResponse(WebSocketMessage.ChatResponseData data) {
        // Build tool calls list
        List<ToolCall> toolCalls = new ArrayList<>();
        if (data.getToolCalls() != null) {
            for (WebSocketMessage.ToolCallData tc : data.getToolCalls()) {
                toolCalls.add(new ToolCall(tc.getId(), tc.getName(), tc.getInput()));
            }
        }

        // Build usage info
        LLMResponse.UsageInfo usage = null;
        if (data.getUsage() != null) {
            usage = new LLMResponse.UsageInfo(
                data.getUsage().getInputTokens(),
                data.getUsage().getOutputTokens()
            );
        }

        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(MessageRole.ASSISTANT)
                .textContent(data.getContent() != null ? data.getContent() : "")
                .build())
            .toolCalls(toolCalls)
            .stopReason(data.getStopReason())
            .usage(usage)
            .build();
    }

    @Override
    public ModelCapability getModelCapability() {
        return modelCapability;
    }

    @Override
    public String getProviderName() {
        return "websocket";
    }

    /**
     * Shutdown and cleanup resources
     */
    public void shutdown() {
        disconnect();
        heartbeatExecutor.shutdown();
    }

    /**
     * Simple model capability for WebSocket provider
     */
    private static class WebSocketModelCapability implements ModelCapability {
        private final String modelId;

        public WebSocketModelCapability(String modelId) {
            this.modelId = modelId;
        }

        @Override
        public String getModelId() {
            return modelId;
        }

        @Override
        public int getMaxContextTokens() {
            return 200000;
        }

        @Override
        public int getMaxOutputTokens() {
            return 8192;
        }

        @Override
        public MessageFormatter getMessageFormatter() {
            return null;
        }

        @Override
        public TokenCounter getTokenCounter() {
            return null;
        }

        @Override
        public Set<ModelFeature> getSupportedFeatures() {
            return Set.of(
                ModelFeature.TOOL_CALLING,
                ModelFeature.STREAMING,
                ModelFeature.SYSTEM_MESSAGE,
                ModelFeature.FUNCTION_CALLING
            );
        }
    }
}
