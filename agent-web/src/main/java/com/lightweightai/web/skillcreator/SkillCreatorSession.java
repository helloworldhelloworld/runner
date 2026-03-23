package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.llm.ConversationMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill Creator 会话状态 - 维护对话历史和当前草稿
 */
public class SkillCreatorSession {

    private final String sessionId;
    private final List<ConversationMessage> history = new ArrayList<>();
    private SkillDraft currentDraft;
    private final long createdAt;

    public SkillCreatorSession(String sessionId) {
        this.sessionId = sessionId;
        this.currentDraft = new SkillDraft();
        this.createdAt = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }

    public List<ConversationMessage> getHistory() { return history; }

    public void addMessage(ConversationMessage message) {
        history.add(message);
    }

    public SkillDraft getCurrentDraft() { return currentDraft; }

    public void setCurrentDraft(SkillDraft draft) { this.currentDraft = draft; }

    public long getCreatedAt() { return createdAt; }
}
