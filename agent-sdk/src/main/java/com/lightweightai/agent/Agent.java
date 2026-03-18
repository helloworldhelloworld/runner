package com.lightweightai.agent;

import com.lightweightai.agent.memory.ConversationMemory;
import com.lightweightai.agent.plugin.Plugin;
import java.util.List;

/**
 * Agent - 主入口接口
 *
 * 这是SDK的核心抽象，代表一个可以对话的AI Agent
 *
 * 示例：
 * <pre>
 * Agent agent = Agent.builder()
 *     .claude(apiKey)
 *     .build();
 *
 * String response = agent.chat("Hello");
 * </pre>
 */
public interface Agent {

    /**
     * 发送消息并获取回复（同步、阻塞）
     *
     * @param message 用户消息
     * @return AI回复
     */
    String chat(String message);

    /**
     * 发送消息并获取回复（带选项）
     *
     * @param message 用户消息
     * @param options 聊天选项
     * @return AI回复
     */
    String chat(String message, ChatOptions options);

    /**
     * 流式对话（实时获取输出）
     *
     * @param message  用户消息
     * @param callback 流式回调
     */
    void chatStream(String message, StreamCallback callback);

    /**
     * 流式对话（带选项）
     *
     * @param message  用户消息
     * @param options  聊天选项
     * @param callback 流式回调
     */
    void chatStream(String message, ChatOptions options, StreamCallback callback);

    /**
     * 动态添加插件
     *
     * @param plugin 插件
     * @return this（支持链式调用）
     */
    Agent addPlugin(Plugin plugin);

    /**
     * 获取所有已注册的插件
     *
     * @return 插件列表
     */
    List<Plugin> getPlugins();

    /**
     * 获取对话记忆（如果启用）
     *
     * @return 记忆对象，如果未启用则返回null
     */
    ConversationMemory getMemory();

    /**
     * 清除对话记忆
     */
    void clearMemory();

    /**
     * 创建Agent构建器
     *
     * @return AgentBuilder实例
     */
    static AgentBuilder builder() {
        return new AgentBuilder();
    }
}
