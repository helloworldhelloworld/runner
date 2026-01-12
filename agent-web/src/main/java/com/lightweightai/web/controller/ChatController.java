package com.lightweightai.web.controller;

import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Chat API controller
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final com.lightweightai.web.service.SoulComfortChatService soulComfortChatService;

    public ChatController(ChatService chatService,
                         com.lightweightai.web.service.SoulComfortChatService soulComfortChatService) {
        this.chatService = chatService;
        this.soulComfortChatService = soulComfortChatService;
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
     * Get session summary (soul comfort mode)
     */
    @GetMapping("/session/{sessionId}/summary")
    public Map<String, Object> getSessionSummary(@PathVariable String sessionId) {
        logger.info("Getting session summary for: {}", sessionId);
        return soulComfortChatService.getSessionSummary(sessionId);
    }

    /**
     * Clear session (soul comfort mode)
     */
    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        logger.info("Clearing session: {}", sessionId);
        soulComfortChatService.clearSession(sessionId);
        return Map.of("status", "cleared", "sessionId", sessionId);
    }
}
