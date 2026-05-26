package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.llm.ConversationMessage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillCreatorSessionTest {

    @Test
    void constructorInitialization() {
        SkillCreatorSession session = new SkillCreatorSession("sess-001");
        assertEquals("sess-001", session.getSessionId());
        assertNotNull(session.getHistory());
        assertTrue(session.getHistory().isEmpty());
        assertNotNull(session.getCurrentDraft());
        assertTrue(session.getCreatedAt() > 0);
    }

    @Test
    void addMessage() {
        SkillCreatorSession session = new SkillCreatorSession("s1");
        ConversationMessage userMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("hello")
                .build();
        ConversationMessage assistantMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("hi")
                .build();
        session.addMessage(userMsg);
        session.addMessage(assistantMsg);
        assertEquals(2, session.getHistory().size());
        assertEquals(ConversationMessage.MessageRole.USER, session.getHistory().get(0).getRole());
        assertEquals(ConversationMessage.MessageRole.ASSISTANT, session.getHistory().get(1).getRole());
    }

    @Test
    void setCurrentDraft() {
        SkillCreatorSession session = new SkillCreatorSession("s1");
        SkillDraft original = session.getCurrentDraft();
        SkillDraft newDraft = new SkillDraft();
        session.setCurrentDraft(newDraft);
        assertSame(newDraft, session.getCurrentDraft());
        assertNotSame(original, session.getCurrentDraft());
    }
}
