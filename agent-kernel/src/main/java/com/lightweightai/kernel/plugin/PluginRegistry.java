package com.lightweightai.kernel.plugin;

import java.util.List;
import java.util.Optional;

/**
 * Registry for managing plugins and their functions
 */
public interface PluginRegistry {

    /**
     * Register a plugin
     *
     * @param plugin The plugin to register
     */
    void register(Plugin plugin);

    /**
     * Unregister a plugin by name
     *
     * @param pluginName Plugin name
     * @return true if plugin was removed
     */
    boolean unregister(String pluginName);

    /**
     * Get a plugin by name
     *
     * @param pluginName Plugin name
     * @return Optional containing the plugin if found
     */
    Optional<Plugin> getPlugin(String pluginName);

    /**
     * Get all registered plugins
     *
     * @return List of all plugins
     */
    List<Plugin> getAllPlugins();

    /**
     * Get a specific function
     *
     * @param functionName Fully qualified function name (e.g., "math.add")
     * @return Optional containing the function if found
     */
    Optional<PluginFunction> getFunction(String functionName);

    /**
     * Get all functions from all plugins
     *
     * @return List of all functions
     */
    List<PluginFunction> getAllFunctions();
}
