package com.lightweightai.kernel.plugin;

import java.util.List;

/**
 * Plugin interface - represents a collection of callable functions.
 * Plugins extend the kernel's capabilities with custom or built-in functionality.
 */
public interface Plugin {

    /**
     * Get the plugin name
     *
     * @return Plugin name (e.g., "math", "time", "weather")
     */
    String getName();

    /**
     * Get the plugin description
     *
     * @return Human-readable description
     */
    String getDescription();

    /**
     * Get all functions provided by this plugin
     *
     * @return List of plugin functions
     */
    List<PluginFunction> getFunctions();
}
