package com.lightweightai.web.controller;

import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.web.config.AgentConfig;
import com.lightweightai.web.config.DynamicLLMProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoints for runtime LLM provider/model configuration.
 * Admin-only: configure provider type, API URL, API key, and model.
 */
@RestController
@RequestMapping("/api/model-config")
@CrossOrigin(origins = "*")
public class ModelConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ModelConfigController.class);

    private final DynamicLLMProvider dynamicProvider;
    private final AgentConfig agentConfig;

    // Store the current runtime config (not persisted across restarts)
    private volatile String currentProviderType = "";
    private volatile String currentApiKey = "";
    private volatile String currentModel = "";
    private volatile String currentBaseUrl = "";

    public ModelConfigController(DynamicLLMProvider dynamicProvider, AgentConfig agentConfig) {
        this.dynamicProvider = dynamicProvider;
        this.agentConfig = agentConfig;
    }

    /**
     * GET /api/model-config - Get current model configuration
     */
    @GetMapping
    public Map<String, Object> getConfig() {
        LLMProvider delegate = dynamicProvider.getDelegate();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("providerType", currentProviderType.isEmpty() ? delegate.getProviderName() : currentProviderType);
        config.put("providerName", delegate.getProviderName());
        config.put("model", currentModel);
        config.put("baseUrl", currentBaseUrl);
        config.put("hasApiKey", !currentApiKey.isEmpty());
        config.put("apiKeyMasked", maskApiKey(currentApiKey));
        return config;
    }

    /**
     * PUT /api/model-config - Update model configuration (admin only)
     */
    @PutMapping
    public Map<String, Object> updateConfig(@RequestBody Map<String, String> body,
                                             HttpServletRequest request) {
        if (!isAdmin(request)) {
            throw new ForbiddenException("权限不足，需要管理员权限");
        }

        String providerType = body.getOrDefault("providerType", "openrouter");
        String apiKey = body.getOrDefault("apiKey", "");
        String model = body.getOrDefault("model", "");
        String baseUrl = body.getOrDefault("baseUrl", "");

        // If apiKey is empty or masked placeholder, keep the existing one
        if (apiKey.isEmpty() || apiKey.startsWith("***")) {
            apiKey = currentApiKey;
        }

        logger.info("Updating model config: providerType={}, model={}, baseUrl={}",
            providerType, model, baseUrl.isEmpty() ? "(default)" : baseUrl);

        LLMProvider newProvider = agentConfig.buildProvider(providerType, apiKey, model, baseUrl);
        dynamicProvider.setDelegate(newProvider);

        // Update stored config
        currentProviderType = providerType;
        currentApiKey = apiKey;
        currentModel = model;
        currentBaseUrl = baseUrl;

        logger.info("Model config updated successfully: provider={}", newProvider.getProviderName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "模型配置已更新");
        result.put("providerName", newProvider.getProviderName());
        result.put("model", model);
        result.put("baseUrl", baseUrl);
        return result;
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equals(request.getAttribute("userRole"));
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) return "";
        return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
    }

    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    static class ForbiddenException extends RuntimeException {
        ForbiddenException(String message) { super(message); }
    }
}
