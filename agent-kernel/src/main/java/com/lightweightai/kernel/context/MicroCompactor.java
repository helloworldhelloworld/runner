package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Micro 压缩：截断超大的 TOOL 消息内容
 *
 * 对超过 maxChars 的 TOOL 消息，保留首尾各 keepChars 字符，
 * 中间替换为 "[...snipped N chars...]"。
 *
 * 非 TOOL 消息不受影响。
 */
public class MicroCompactor implements ContextCompactor {

    private final int maxChars;
    private final int keepChars;

    /**
     * @param maxChars 超过此字符数的 TOOL 消息会被截断
     */
    public MicroCompactor(int maxChars) {
        this(maxChars, Math.min(200, maxChars / 4));
    }

    public MicroCompactor(int maxChars, int keepChars) {
        this.maxChars = maxChars;
        this.keepChars = keepChars;
    }

    @Override
    public List<ConversationMessage> compact(List<ConversationMessage> messages) {
        List<ConversationMessage> result = new ArrayList<>(messages.size());
        for (ConversationMessage msg : messages) {
            if (msg.getRole() == MessageRole.TOOL) {
                String text = msg.getTextContent();
                if (text != null && text.length() > maxChars) {
                    String snipped = truncate(text);
                    result.add(ConversationMessage.builder()
                            .role(MessageRole.TOOL)
                            .textContent(snipped)
                            .metadata(msg.getMetadata())
                            .build());
                    continue;
                }
            }
            result.add(msg);
        }
        return result;
    }

    private String truncate(String text) {
        int snippedCount = text.length() - (keepChars * 2);
        return text.substring(0, keepChars)
                + "\n[...snipped " + snippedCount + " chars...]\n"
                + text.substring(text.length() - keepChars);
    }
}
