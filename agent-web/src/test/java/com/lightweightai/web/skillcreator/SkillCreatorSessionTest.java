package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.llm.ConversationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SkillCreatorSession} — per-session state holder.
 */
@DisplayName("SkillCreatorSession - 会话状态")
class SkillCreatorSessionTest {

    @Test
    @DisplayName("构造函数初始化所有字段")
    void constructor_initializesFields() {
        long before = System.currentTimeMillis();
        SkillCreatorSession session = new SkillCreatorSession("sess-1");
        long after = System.currentTimeMillis();

        assertEquals("sess-1", session.getSessionId());
        assertNotNull(session.getHistory());
        assertTrue(session.getHistory().isEmpty());
        assertNotNull(session.getCurrentDraft());
        assertTrue(session.getCreatedAt() >= before && session.getCreatedAt() <= after);
    }

    @Test
    @DisplayName("addMessage 追加到历史列表")
    void addMessage_appendsToHistory() {
        SkillCreatorSession session = new SkillCreatorSession("sess-2");

        ConversationMessage msg1 = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent("Hello")
            .build();
        ConversationMessage msg2 = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent("Hi there")
            .build();

        session.addMessage(msg1);
        session.addMessage(msg2);

        assertEquals(2, session.getHistory().size());
        assertEquals("Hello", session.getHistory().get(0).getTextContent());
        assertEquals("Hi there", session.getHistory().get(1).getTextContent());
    }

    @Test
    @DisplayName("getHistory 返回可变列表（同一引用）")
    void getHistory_returnsMutableReference() {
        SkillCreatorSession session = new SkillCreatorSession("sess-3");
        assertSame(session.getHistory(), session.getHistory());
    }

    @Test
    @DisplayName("setCurrentDraft 替换当前 draft")
    void setCurrentDraft_replaces() {
        SkillCreatorSession session = new SkillCreatorSession("sess-4");
        assertNull(session.getCurrentDraft().getName());

        SkillDraft draft = new SkillDraft();
        draft.setName("my-skill");
        session.setCurrentDraft(draft);

        assertEquals("my-skill", session.getCurrentDraft().getName());
    }
}
