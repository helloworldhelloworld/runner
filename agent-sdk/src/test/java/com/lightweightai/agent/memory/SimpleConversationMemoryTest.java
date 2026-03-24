package com.lightweightai.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimpleConversationMemory")
class SimpleConversationMemoryTest {

    private SimpleConversationMemory memory;

    @BeforeEach
    void setUp() {
        memory = new SimpleConversationMemory();
    }

    @Test
    @DisplayName("should return empty history when no messages added")
    void emptyMemoryReturnsEmptyHistory() {
        List<String> history = memory.getHistory();
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("should add messages and return them in history")
    void addMessagesAndVerifyHistory() {
        memory.addMessage("user", "Hello");
        memory.addMessage("assistant", "Hi there");

        List<String> history = memory.getHistory();
        assertEquals(2, history.size());
        assertEquals("Hello", history.get(0));
        assertEquals("Hi there", history.get(1));
    }

    @Test
    @DisplayName("should return unmodifiable list from getHistory")
    void getHistoryReturnsUnmodifiableList() {
        memory.addMessage("user", "Hello");

        List<String> history = memory.getHistory();
        assertThrows(UnsupportedOperationException.class, () -> history.add("should fail"));
    }

    @Test
    @DisplayName("should track message count correctly")
    void getMessageCountTracksCorrectly() {
        assertEquals(0, memory.getMessageCount());

        memory.addMessage("user", "First");
        assertEquals(1, memory.getMessageCount());

        memory.addMessage("assistant", "Second");
        assertEquals(2, memory.getMessageCount());

        memory.addMessage("user", "Third");
        assertEquals(3, memory.getMessageCount());
    }

    @Test
    @DisplayName("should clear all messages")
    void clearEmptiesMessages() {
        memory.addMessage("user", "Hello");
        memory.addMessage("assistant", "Hi");
        assertEquals(2, memory.getMessageCount());

        memory.clear();

        assertEquals(0, memory.getMessageCount());
        assertTrue(memory.getHistory().isEmpty());
    }

    @Test
    @DisplayName("should store content only, ignoring role")
    void storesContentOnly() {
        memory.addMessage("user", "question");
        memory.addMessage("assistant", "answer");

        List<String> history = memory.getHistory();
        assertEquals("question", history.get(0));
        assertEquals("answer", history.get(1));
    }
}
