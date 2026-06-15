package com.lightweightai.web.websocket;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** 每会话调用链广播：viewer 订阅后收到该 session 的事件，且会话间隔离。 */
@DisplayName("SessionStreamRegistry: 订阅后收事件 + 会话隔离")
class SessionStreamRegistryTest {

    @Test
    void observerReceivesEventsForItsSessionOnly() {
        SessionStreamRegistry reg = new SessionStreamRegistry();
        List<StreamEvent> got = new CopyOnWriteArrayList<>();

        Disposable d = reg.observe("s1").subscribe(got::add);

        reg.publish("s1", StreamEvent.textDelta("你好"));
        reg.publish("s1", StreamEvent.speakableChunk("讲个笑话", 0, "happy"));
        reg.publish("other", StreamEvent.textDelta("不该收到")); // 别的会话

        assertEquals(2, got.size(), "只收到本会话 s1 的两个事件");
        assertEquals(StreamEvent.EventType.SPEAKABLE_CHUNK, got.get(1).getType());

        d.dispose();
    }

    @Test
    void noSubscriberDoesNotThrowOrLeak() {
        SessionStreamRegistry reg = new SessionStreamRegistry();
        // 无 viewer 时 publish 应安全丢弃（best-effort，不积压、不抛）
        reg.publish("idle", StreamEvent.textDelta("x"));
        reg.publish(null, StreamEvent.textDelta("y"));
    }
}
