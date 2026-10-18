package com.lightweightai.web.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ModelPresetStoreTest {
    @Test void persistsMetadataWithoutApiKeyAndReloads() throws Exception {
        Path dir = Files.createTempDirectory("model-presets");
        Path file = dir.resolve("presets.json");
        ModelPresetStore store = new ModelPresetStore(file);
        store.put("local", Map.of("providerType", "openrouter", "model", "m1", "baseUrl", "http://peer", "apiKey", "secret"));
        String raw = Files.readString(file);
        assertFalse(raw.contains("secret"));
        ModelPresetStore reloaded = new ModelPresetStore(file);
        assertEquals("m1", reloaded.get("local").get("model"));
        assertFalse(reloaded.get("local").containsKey("apiKey"));
    }
}
