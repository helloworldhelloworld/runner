package com.lightweightai.web.service;

import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.instruction.InstructionRegistry;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chat service handling LLM interactions
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final LLMProvider llmProvider;
    private final ToolCallingLoop toolCallingLoop;
    private final InstructionRegistry instructionRegistry;
    private final com.lightweightai.kernel.core.ToolExecutor toolExecutor;

    public ChatService(
        LLMProvider llmProvider,
        ToolCallingLoop toolCallingLoop,
        InstructionRegistry instructionRegistry,
        com.lightweightai.kernel.core.ToolExecutor toolExecutor
    ) {
        this.llmProvider = llmProvider;
        this.toolCallingLoop = toolCallingLoop;
        this.instructionRegistry = instructionRegistry;
        this.toolExecutor = toolExecutor;
    }

    /**
     * Process chat request
     */
    public ChatResponse chat(ChatRequest request) {
        try {
            logger.info("Processing chat request: {}", request.getMessage());

            // Activate requested skills
            instructionRegistry.deactivateAll();
            if (request.getActiveSkills() != null) {
                request.getActiveSkills().forEach(skill -> {
                    try {
                        instructionRegistry.activatePackage(skill);
                        logger.info("Activated skill: {}", skill);
                    } catch (Exception e) {
                        logger.warn("Failed to activate skill {}: {}", skill, e.getMessage());
                    }
                });
            }

            // Build messages
            List<ConversationMessage> messages = new ArrayList<>();
            messages.add(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent(request.getMessage())
                .build());

            // Inject skills if any active
            if (instructionRegistry.getActivePackageCount() > 0) {
                messages = instructionRegistry.injectInstructions(messages);
                logger.info("Injected {} active skills", instructionRegistry.getActivePackageCount());
            }

            // Build options
            LLMOptions options = LLMOptions.builder()
                .maxTokens(2048)
                .temperature(0.7)
                .build();

            // Add tool definitions if tool calling is enabled
            if (request.isUseToolCalling()) {
                options = LLMOptions.builder()
                    .maxTokens(2048)
                    .temperature(0.7)
                    .toolDefinitions(toolExecutor.getToolDefinitions())
                    .build();
            }

            // Execute
            LLMResponse llmResponse;
            if (request.isUseToolCalling()) {
                logger.info("Using tool calling");
                llmResponse = toolCallingLoop.executeWithTools(messages, options);
            } else {
                logger.info("Direct LLM call");
                llmResponse = llmProvider.complete(messages, options);
            }

            // Build response
            ChatResponse response = new ChatResponse();
            response.setResponse(llmResponse.getMessage().getTextContent());

            if (llmResponse.getToolCalls() != null && !llmResponse.getToolCalls().isEmpty()) {
                response.setToolCallsUsed(
                    llmResponse.getToolCalls().stream()
                        .map(ToolCall::getName)
                        .collect(Collectors.toList())
                );
            }

            if (instructionRegistry.getActivePackageCount() > 0) {
                response.setSkillsApplied(
                    instructionRegistry.getActivePackages().stream()
                        .map(pkg -> pkg.getName())
                        .collect(Collectors.toList())
                );
            }

            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("provider", llmProvider.getProviderName());
            metadata.put("toolCallingEnabled", request.isUseToolCalling());
            metadata.put("skillsCount", instructionRegistry.getActivePackageCount());
            response.setMetadata(metadata);

            logger.info("Chat completed successfully");
            return response;

        } catch (Exception e) {
            logger.error("Chat failed", e);
            return ChatResponse.error(e.getMessage());
        }
    }

    /**
     * Get available skills
     */
    public List<Map<String, String>> getAvailableSkills() {
        return instructionRegistry.getAllPackages().stream()
            .map(pkg -> {
                Map<String, String> info = new HashMap<>();
                info.put("name", pkg.getName());
                info.put("description", pkg.getDescription());
                info.put("format", pkg.getFormat());
                return info;
            })
            .collect(Collectors.toList());
    }

    /**
     * Get available tools
     */
    public List<Map<String, Object>> getAvailableTools() {
        return toolExecutor.getToolDefinitions();
    }
}
