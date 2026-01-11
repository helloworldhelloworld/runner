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

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Chat endpoint
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        logger.info("Received chat request");
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
            "tools", chatService.getAvailableTools().size()
        );
    }
}
