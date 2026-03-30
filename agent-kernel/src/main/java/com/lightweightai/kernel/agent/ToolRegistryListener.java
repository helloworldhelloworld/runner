package com.lightweightai.kernel.agent;

/**
 * 工具注册表变更监听器
 *
 * 当 ToolRegistry 中的工具发生注册、注销、启用、禁用时触发回调。
 * 用于 MCP 服务端动态同步工具列表等场景。
 */
public interface ToolRegistryListener {

    /**
     * 工具被注册时触发
     */
    default void onToolRegistered(Tool tool) {}

    /**
     * 工具被注销时触发
     */
    default void onToolUnregistered(String toolName) {}

    /**
     * 工具被启用时触发
     */
    default void onToolEnabled(String toolName) {}

    /**
     * 工具被禁用时触发
     */
    default void onToolDisabled(String toolName) {}
}
