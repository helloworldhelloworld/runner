package com.lightweightai.web.controller;

import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.web.config.AgentConfig;
import com.lightweightai.web.config.DynamicLLMProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    // Saved configuration presets: name -> config map
    private final Map<String, Map<String, String>> savedPresets = new ConcurrentHashMap<>();

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

    /**
     * GET /api/model-config/presets - List all saved presets
     */
    @GetMapping("/presets")
    public List<Map<String, Object>> listPresets() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : savedPresets.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("providerType", entry.getValue().get("providerType"));
            item.put("model", entry.getValue().get("model"));
            item.put("baseUrl", entry.getValue().get("baseUrl"));
            item.put("apiKeyMasked", maskApiKey(entry.getValue().get("apiKey")));
            list.add(item);
        }
        return list;
    }

    /**
     * POST /api/model-config/presets - Save current config as a named preset
     */
    @PostMapping("/presets")
    public Map<String, Object> savePreset(@RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        if (!isAdmin(request)) {
            throw new ForbiddenException("权限不足，需要管理员权限");
        }
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("预设名称不能为空");
        }
        name = name.trim();

        Map<String, String> preset = new LinkedHashMap<>();
        preset.put("providerType", currentProviderType);
        preset.put("apiKey", currentApiKey);
        preset.put("model", currentModel);
        preset.put("baseUrl", currentBaseUrl);
        savedPresets.put(name, preset);

        logger.info("Saved model config preset: {}", name);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "预设 \"" + name + "\" 已保存");
        return result;
    }

    /**
     * PUT /api/model-config/presets/{name}/apply - Load and apply a saved preset
     */
    @PutMapping("/presets/{name}/apply")
    public Map<String, Object> applyPreset(@PathVariable("name") String name,
                                            HttpServletRequest request) {
        if (!isAdmin(request)) {
            throw new ForbiddenException("权限不足，需要管理员权限");
        }
        Map<String, String> preset = savedPresets.get(name);
        if (preset == null) {
            throw new IllegalArgumentException("预设 \"" + name + "\" 不存在");
        }

        String providerType = preset.get("providerType");
        String apiKey = preset.get("apiKey");
        String model = preset.get("model");
        String baseUrl = preset.get("baseUrl");

        LLMProvider newProvider = agentConfig.buildProvider(providerType, apiKey, model, baseUrl);
        dynamicProvider.setDelegate(newProvider);

        currentProviderType = providerType;
        currentApiKey = apiKey;
        currentModel = model;
        currentBaseUrl = baseUrl;

        logger.info("Applied model config preset: {}", name);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已切换到预设 \"" + name + "\"");
        result.put("providerName", newProvider.getProviderName());
        result.put("model", model);
        result.put("baseUrl", baseUrl);
        return result;
    }

    /**
     * DELETE /api/model-config/presets/{name} - Delete a saved preset
     */
    @DeleteMapping("/presets/{name}")
    public Map<String, Object> deletePreset(@PathVariable("name") String name,
                                             HttpServletRequest request) {
        if (!isAdmin(request)) {
            throw new ForbiddenException("权限不足，需要管理员权限");
        }
        if (savedPresets.remove(name) == null) {
            throw new IllegalArgumentException("预设 \"" + name + "\" 不存在");
        }
        logger.info("Deleted model config preset: {}", name);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "预设 \"" + name + "\" 已删除");
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
