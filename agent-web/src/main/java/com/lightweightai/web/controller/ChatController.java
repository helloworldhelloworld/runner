package com.lightweightai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.observer.DebugAgentObserver;
import com.lightweightai.web.service.ChatService;
import com.lightweightai.web.service.SoulComfortChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chat API controller
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final SoulComfortChatService soulComfortChatService;
    private final ObjectMapper compactMapper; // SSE需要单行JSON
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService,
                         SoulComfortChatService soulComfortChatService,
                         ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.soulComfortChatService = soulComfortChatService;
        // 创建紧凑格式的ObjectMapper用于SSE
        this.compactMapper = objectMapper.copy()
            .disable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Chat endpoint
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        logger.info("Received chat request (soul comfort mode: {})", request.isSoulComfortMode());

        // 使用心灵引导模式
        if (request.isSoulComfortMode()) {
            return soulComfortChatService.chat(request);
        }

        // 使用普通模式
        return chatService.chat(request);
    }

    /**
     * Streaming chat endpoint (SSE)
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        logger.info("Received streaming chat request (session: {})", request.getSessionId());

        SseEmitter emitter = new SseEmitter(60000L); // 60 second timeout

        executor.submit(() -> {
            try {
                String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
                DebugAgentObserver debugObserver = request.isDebug() ? new DebugAgentObserver() : null;

                soulComfortChatService.chat(request.getMessage(), sessionId,
                    chunk -> {
                        try {
                            // Send delta event
                            Map<String, Object> event = Map.of(
                                "type", "delta",
                                "delta", chunk.getDelta(),
                                "emotion", chunk.getEmotion() != null ? chunk.getEmotion() : ""
                            );
                            emitter.send(SseEmitter.event()
                                .name("message")
                                .data(compactMapper.writeValueAsString(event)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE event", e);
                        }
                    }, debugObserver
                ).thenAccept(fullResponse -> {
                    try {
                        // Send complete event (含 debug info 如果开启了调试模式)
                        Map<String, Object> completeEvent = new java.util.HashMap<>();
                        completeEvent.put("type", "complete");
                        completeEvent.put("response", fullResponse);
                        completeEvent.put("skillsApplied", List.of("soul-comfort", "openclaw-memory"));
                        if (debugObserver != null) {
                            completeEvent.put("debug", debugObserver.getDebugInfo());
                        }
                        emitter.send(SseEmitter.event()
                            .name("message")
                            .data(compactMapper.writeValueAsString(completeEvent)));
                        emitter.complete();
                    } catch (IOException e) {
                        logger.error("Failed to send complete event", e);
                        emitter.completeWithError(e);
                    }
                }).exceptionally(error -> {
                    logger.error("Streaming chat error", error);
                    try {
                        Map<String, Object> errorEvent = Map.of(
                            "type", "error",
                            "message", error.getMessage() != null ? error.getMessage() : "Unknown error"
                        );
                        emitter.send(SseEmitter.event()
                            .name("error")
                            .data(compactMapper.writeValueAsString(errorEvent)));
                    } catch (IOException e) {
                        logger.error("Failed to send error event", e);
                    }
                    emitter.completeWithError(error);
                    return null;
                });

            } catch (Exception e) {
                logger.error("Failed to start streaming chat", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> logger.debug("SSE connection completed"));
        emitter.onTimeout(() -> logger.warn("SSE connection timed out"));
        emitter.onError(e -> logger.error("SSE error", e));

        return emitter;
    }

    /**
     * Get available skills
     */
    @GetMapping("/skills")
    public List<Map<String, String>> getSkills() {
        return chatService.getAvailableSkills();
    }

    /**
     * Get available tools
     */
    @GetMapping("/tools")
    public List<Map<String, Object>> getTools() {
        return chatService.getAvailableTools();
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "skills", chatService.getAvailableSkills().size(),
            "tools", chatService.getAvailableTools().size(),
            "soulComfortMode", "enabled"
        );
    }

    /**
     * Get session history (for restoring UI after refresh)
     */
    @GetMapping("/session/{sessionId}/history")
    public List<Map<String, String>> getSessionHistory(@PathVariable("sessionId") String sessionId) {
        logger.info("Getting session history for: {}", sessionId);
        return soulComfortChatService.getSessionHistory(sessionId);
    }

    /**
     * Get session summary (soul comfort mode)
     */
    @GetMapping("/session/{sessionId}/summary")
    public Map<String, Object> getSessionSummary(@PathVariable("sessionId") String sessionId) {
        logger.info("Getting session summary for: {}", sessionId);
        return soulComfortChatService.getSessionSummary(sessionId);
    }

    /**
     * Clear session (soul comfort mode)
     */
    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable("sessionId") String sessionId) {
        logger.info("Clearing session: {}", sessionId);
        soulComfortChatService.clearSession(sessionId);
        return Map.of("status", "cleared", "sessionId", sessionId);
    }
}
