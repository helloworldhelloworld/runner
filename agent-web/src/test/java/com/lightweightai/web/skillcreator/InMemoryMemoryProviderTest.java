package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryMemoryProvider — lightweight in-memory session storage")
class InMemoryMemoryProviderTest {

    private InMemoryMemoryProvider provider;

    @BeforeEach
    void setup() {
        provider = new InMemoryMemoryProvider();
    }

    @Nested
    @DisplayName("addMessage + getHistory")
    class AddAndGet {

        @Test
        @DisplayName("stores and retrieves messages for a session")
        void storesAndRetrieves() {
            provider.addMessage("s1", Message.user("hello"));
            provider.addMessage("s1", Message.assistant("hi there"));

            List<Message> history = provider.getHistory("s1", 10);
            assertEquals(2, history.size());
            assertEquals("user", history.get(0).getRole());
            assertEquals("hello", history.get(0).getContent());
            assertEquals("assistant", history.get(1).getRole());
            assertEquals("hi there", history.get(1).getContent());
        }

        @Test
        @DisplayName("returns empty list for unknown session")
        void emptyForUnknown() {
            List<Message> history = provider.getHistory("unknown", 10);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("limit returns most recent N messages")
        void limitReturnsMostRecent() {
            provider.addMessage("s1", Message.user("first"));
            provider.addMessage("s1", Message.assistant("second"));
            provider.addMessage("s1", Message.user("third"));

            List<Message> history = provider.getHistory("s1", 2);
            assertEquals(2, history.size());
            assertEquals("second", history.get(0).getContent());
            assertEquals("third", history.get(1).getContent());
        }

        @Test
        @DisplayName("limit larger than history returns all messages")
        void limitLargerThanHistory() {
            provider.addMessage("s1", Message.user("only"));
            List<Message> history = provider.getHistory("s1", 100);
            assertEquals(1, history.size());
        }
    }

    @Nested
    @DisplayName("session isolation")
    class SessionIsolation {

        @Test
        @DisplayName("different sessions have independent histories")
        void isolatedSessions() {
            provider.addMessage("s1", Message.user("from s1"));
            provider.addMessage("s2", Message.user("from s2"));

            List<Message> h1 = provider.getHistory("s1", 10);
            List<Message> h2 = provider.getHistory("s2", 10);

            assertEquals(1, h1.size());
            assertEquals("from s1", h1.get(0).getContent());
            assertEquals(1, h2.size());
            assertEquals("from s2", h2.get(0).getContent());
        }
    }

    @Nested
    @DisplayName("clearSession")
    class Clear {

        @Test
        @DisplayName("removes all messages for the session")
        void clearsSession() {
            provider.addMessage("s1", Message.user("hello"));
            provider.clearSession("s1");

            List<Message> history = provider.getHistory("s1", 10);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("clearing one session does not affect others")
        void clearDoesNotAffectOthers() {
            provider.addMessage("s1", Message.user("s1 msg"));
            provider.addMessage("s2", Message.user("s2 msg"));
            provider.clearSession("s1");

            assertTrue(provider.getHistory("s1", 10).isEmpty());
            assertEquals(1, provider.getHistory("s2", 10).size());
        }
    }

    @Nested
    @DisplayName("no-op methods")
    class NoOps {

        @Test
        @DisplayName("writeEphemeral does not throw")
        void writeEphemeralNoOp() {
            assertDoesNotThrow(() -> provider.writeEphemeral("some content"));
        }

        @Test
        @DisplayName("writeDurable does not throw")
        void writeDurableNoOp() {
            assertDoesNotThrow(() -> provider.writeDurable("section", "content"));
        }

        @Test
        @DisplayName("search returns empty list")
        void searchReturnsEmpty() {
            List<MemorySearchResult> results = provider.search("anything");
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("MemoryProvider contract")
    class Contract {

        @Test
        @DisplayName("implements MemoryProvider interface")
        void implementsInterface() {
            assertInstanceOf(MemoryProvider.class, provider);
        }

        @Test
        @DisplayName("returned history is a defensive copy — modifying it doesn't affect storage")
        void defensiveCopy() {
            provider.addMessage("s1", Message.user("hello"));
            List<Message> history = provider.getHistory("s1", 10);
            history.clear();

            assertEquals(1, provider.getHistory("s1", 10).size(),
                    "Clearing the returned list should not affect stored history");
        }
    }
}
