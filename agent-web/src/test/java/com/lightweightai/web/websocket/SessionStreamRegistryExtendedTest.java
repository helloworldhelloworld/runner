package com.lightweightai.web.websocket;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionStreamRegistry - extended coverage")
class SessionStreamRegistryExtendedTest {

    @Test
    @DisplayName("multiple observers on same session all receive events")
    void multipleObserversReceiveEvents() {
        SessionStreamRegistry reg = new SessionStreamRegistry();
        List<StreamEvent> got1 = new CopyOnWriteArrayList<>();
        List<StreamEvent> got2 = new CopyOnWriteArrayList<>();

        Disposable d1 = reg.observe("s1").subscribe(got1::add);
        Disposable d2 = reg.observe("s1").subscribe(got2::add);

        reg.publish("s1", StreamEvent.textDelta("hello"));

        assertEquals(1, got1.size());
        assertEquals(1, got2.size());
        assertEquals("hello", got1.get(0).getTextDelta());
        assertEquals("hello", got2.get(0).getTextDelta());

        d1.dispose();
        d2.dispose();
    }

    @Test
    @DisplayName("late subscriber does not receive events published before subscription")
    void lateSubscriberMissesOldEvents() {
        SessionStreamRegistry reg = new SessionStreamRegistry();

        reg.publish("s1", StreamEvent.textDelta("before"));

        List<StreamEvent> got = new CopyOnWriteArrayList<>();
        Disposable d = reg.observe("s1").subscribe(got::add);

        reg.publish("s1", StreamEvent.textDelta("after"));

        assertEquals(1, got.size(), "Should only see event published after subscription");
        assertEquals("after", got.get(0).getTextDelta());

        d.dispose();
    }

    @Test
    @DisplayName("disposed observer stops receiving events")
    void disposedObserverStopsReceiving() {
        SessionStreamRegistry reg = new SessionStreamRegistry();
        List<StreamEvent> got = new CopyOnWriteArrayList<>();

        Disposable d = reg.observe("s1").subscribe(got::add);
        reg.publish("s1", StreamEvent.textDelta("one"));
        assertEquals(1, got.size());

        d.dispose();

        reg.publish("s1", StreamEvent.textDelta("two"));
        assertEquals(1, got.size(), "Disposed observer should not receive new events");
    }

    @Test
    @DisplayName("publish after all observers disposed does not throw")
    void publishAfterAllDisposedIsSafe() {
        SessionStreamRegistry reg = new SessionStreamRegistry();
        Disposable d = reg.observe("s1").subscribe(e -> {});
        d.dispose();

        assertDoesNotThrow(() -> reg.publish("s1", StreamEvent.textDelta("orphan")));
    }

    @Test
    @DisplayName("different sessions are fully isolated")
    void sessionsAreIsolated() {
        SessionStreamRegistry reg = new SessionStreamRegistry();
        List<StreamEvent> gotA = new CopyOnWriteArrayList<>();
        List<StreamEvent> gotB = new CopyOnWriteArrayList<>();

        Disposable dA = reg.observe("a").subscribe(gotA::add);
        Disposable dB = reg.observe("b").subscribe(gotB::add);

        reg.publish("a", StreamEvent.textDelta("for-a"));
        reg.publish("b", StreamEvent.textDelta("for-b"));

        assertEquals(1, gotA.size());
        assertEquals("for-a", gotA.get(0).getTextDelta());
        assertEquals(1, gotB.size());
        assertEquals("for-b", gotB.get(0).getTextDelta());

        dA.dispose();
        dB.dispose();
    }

    @Test
    @DisplayName("EventType is correctly preserved through publish/observe")
    void eventTypePreserved() {
        SessionStreamRegistry reg = new SessionStreamRegistry();
        List<StreamEvent> got = new CopyOnWriteArrayList<>();

        Disposable d = reg.observe("s1").subscribe(got::add);

        reg.publish("s1", StreamEvent.textDelta("text"));
        reg.publish("s1", StreamEvent.speakableChunk("speech", 0, "happy"));

        assertEquals(StreamEvent.EventType.TEXT_DELTA, got.get(0).getType());
        assertEquals(StreamEvent.EventType.SPEAKABLE_CHUNK, got.get(1).getType());

        d.dispose();
    }
}
