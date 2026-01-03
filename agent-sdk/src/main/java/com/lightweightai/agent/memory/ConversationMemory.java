package com.lightweightai.agent.memory;

import java.util.List;

/**
 * 对话记忆接口
 *
 * 简化版，只暴露用户需要的方法
 */
public interface ConversationMemory {

    /**
     * 添加一条消息
     *
     * @param role 角色（user 或 assistant）
     * @param content 消息内容
     */
    void addMessage(String role, String content);

    /**
     * 获取消息历史（按时间顺序）
     *
     * @return 消息列表（交替的用户和助手消息）
     */
    List<String> getHistory();

    /**
     * 获取消息数量
     *
     * @return 消息数量
     */
    int getMessageCount();

    /**
     * 清除所有消息
     */
    void clear();
}
