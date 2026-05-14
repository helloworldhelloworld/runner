package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillCreatorSession - 会话状态管理")
class SkillCreatorSessionTest {

    private SkillCreatorSession session;

    @BeforeEach
    void setUp() {
        session = new SkillCreatorSession("test-session-1");
    }

    @Test
    @DisplayName("构造时设置 sessionId 和 createdAt")
    void constructorSetsSessionIdAndCreatedAt() {
        assertEquals("test-session-1", session.getSessionId());
        assertTrue(session.getCreatedAt() > 0);
        assertTrue(session.getCreatedAt() <= System.currentTimeMillis());
    }

    @Test
    @DisplayName("构造时初始化空草稿")
    void constructorInitializesEmptyDraft() {
        SkillDraft draft = session.getCurrentDraft();
        assertNotNull(draft);
        assertFalse(draft.isValid(), "empty draft should not be valid");
    }

    @Test
    @DisplayName("添加消息到历史并按序保留")
    void addMessagePreservesOrder() {
        ConversationMessage msg1 = ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent("hello")
            .build();
        ConversationMessage msg2 = ConversationMessage.builder()
            .role(MessageRole.ASSISTANT)
            .textContent("hi there")
            .build();

        session.addMessage(msg1);
        session.addMessage(msg2);

        assertEquals(2, session.getHistory().size());
        assertEquals(MessageRole.USER, session.getHistory().get(0).getRole());
        assertEquals(MessageRole.ASSISTANT, session.getHistory().get(1).getRole());
        assertEquals("hello", session.getHistory().get(0).getTextContent());
        assertEquals("hi there", session.getHistory().get(1).getTextContent());
    }

    @Test
    @DisplayName("初始历史为空列表")
    void historyStartsEmpty() {
        assertTrue(session.getHistory().isEmpty());
    }

    @Test
    @DisplayName("setCurrentDraft 替换草稿")
    void setCurrentDraftReplacesDraft() {
        SkillDraft newDraft = new SkillDraft("draft-1");
        newDraft.setName("test-skill");
        newDraft.setDescription("A test skill");
        newDraft.setSystemPrompt("You are a test assistant");

        session.setCurrentDraft(newDraft);

        assertEquals("draft-1", session.getCurrentDraft().getId());
        assertEquals("test-skill", session.getCurrentDraft().getName());
        assertTrue(session.getCurrentDraft().isValid());
    }
}
