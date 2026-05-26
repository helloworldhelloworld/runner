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
        session.addMessage(ConversationMessage.user("hello"));
        session.addMessage(ConversationMessage.assistant("hi"));
        assertEquals(2, session.getHistory().size());
        assertEquals("user", session.getHistory().get(0).getRole());
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
