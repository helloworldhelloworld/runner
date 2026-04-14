package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Snip 压缩：删除旧的 TOOL role 消息
 *
 * 保留最近 keepRecentRounds 轮的 TOOL 消息，更早的删除。
 * 一"轮"定义为一对 USER + ASSISTANT 消息。
 *
 * USER/ASSISTANT/SYSTEM 消息永远保留。
 */
public class SnipCompactor implements ContextCompactor {

    private final int keepRecentRounds;

    /**
     * @param keepRecentRounds 保留最近几轮的 TOOL 消息（默认建议 3）
     */
    public SnipCompactor(int keepRecentRounds) {
        this.keepRecentRounds = keepRecentRounds;
    }

    @Override
    public List<ConversationMessage> compact(List<ConversationMessage> messages) {
        // 统计总轮数（以 USER 消息计）
        long totalUserMsgs = messages.stream()
                .filter(m -> m.getRole() == MessageRole.USER)
                .count();

        if (totalUserMsgs <= keepRecentRounds) {
            return messages; // 不够轮数，不删
        }

        // 找到"保护线"：从后往前数 keepRecentRounds 个 USER 消息的位置
        int protectFrom = findProtectBoundary(messages);

        List<ConversationMessage> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage msg = messages.get(i);
            if (msg.getRole() == MessageRole.TOOL && i < protectFrom) {
                continue; // 旧 TOOL 消息，跳过
            }
            result.add(msg);
        }
        return result;
    }

    /**
     * 找保护线：从后往前数第 keepRecentRounds 个 USER 消息的位置。
     * 该位置及之后的所有消息（含 TOOL）都保留。
     */
    private int findProtectBoundary(List<ConversationMessage> messages) {
        int userCount = 0;
        int boundary = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == MessageRole.USER) {
                userCount++;
                if (userCount == keepRecentRounds) {
                    boundary = i;
                }
            }
        }
        return boundary;
    }
}
