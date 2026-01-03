package com.lightweightai.kernel.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for plugins
 */
public class PluginConfiguration {

    private List<String> enabledBuiltins = new ArrayList<>();
    private List<CustomPluginConfig> custom = new ArrayList<>();
    private Map<String, Map<String, Object>> pluginSettings = new HashMap<>();

    public List<String> getEnabledBuiltins() {
        return enabledBuiltins;
    }

    public void setEnabledBuiltins(List<String> enabledBuiltins) {
        this.enabledBuiltins = enabledBuiltins;
    }

    public List<CustomPluginConfig> getCustom() {
        return custom;
    }

    public void setCustom(List<CustomPluginConfig> custom) {
        this.custom = custom;
    }

    public Map<String, Map<String, Object>> getPluginSettings() {
        return pluginSettings;
    }

    public void setPluginSettings(Map<String, Map<String, Object>> pluginSettings) {
        this.pluginSettings = pluginSettings;
    }

    /**
     * Custom plugin configuration
     */
    public static class CustomPluginConfig {
        private String name;
        private String className;
        private boolean enabled = true;
        private Map<String, Object> settings = new HashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, Object> getSettings() {
            return settings;
        }

        public void setSettings(Map<String, Object> settings) {
            this.settings = settings;
        }
    }
}
