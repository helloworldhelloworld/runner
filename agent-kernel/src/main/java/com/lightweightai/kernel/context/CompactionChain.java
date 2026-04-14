package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;

import java.util.List;

/**
 * 链式压缩：按序执行多个 ContextCompactor
 *
 * 典型用法：new CompactionChain(new SnipCompactor(3), new MicroCompactor(2000))
 */
public class CompactionChain implements ContextCompactor {

    private final List<ContextCompactor> stages;

    public CompactionChain(ContextCompactor... stages) {
        this.stages = List.of(stages);
    }

    @Override
    public List<ConversationMessage> compact(List<ConversationMessage> messages) {
        List<ConversationMessage> result = messages;
        for (ContextCompactor stage : stages) {
            result = stage.compact(result);
        }
        return result;
    }
}
