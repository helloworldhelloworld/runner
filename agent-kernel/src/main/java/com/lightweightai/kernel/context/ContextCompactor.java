package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;

import java.util.List;

/**
 * 上下文压缩接口
 *
 * 在 AgentLoop 构建消息后、传给 LLM 前，对消息列表进行压缩，
 * 防止超过模型的 context window。
 */
public interface ContextCompactor {

    /**
     * 压缩消息列表
     *
     * @param messages 当前对话消息（可变副本，可直接修改）
     * @return 压缩后的消息列表
     */
    List<ConversationMessage> compact(List<ConversationMessage> messages);
}
