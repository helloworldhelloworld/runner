package com.lightweightai.openclaw;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** OpenClawRun 取消路径单测：interrupt = ACP cancel + dispose 订阅 + 转 INTERRUPTED + 返回 SPEECH_INTERRUPTED。 */
@DisplayName("OpenClawRun - 取消路径")
class OpenClawRunTest {

    @Test
    @DisplayName("interrupt() 取消 OpenClaw run、dispose 上游订阅、转 INTERRUPTED、返回 SPEECH_INTERRUPTED")
    void interruptCancelsDisposesAndReturnsSpeechInterrupted() {
        FakeOpenClawClient client = new FakeOpenClawClient();
        OpenClawRun run = new OpenClawRun("s1");
        run.setOpenClawRunId("r1");

        AtomicBoolean disposed = new AtomicBoolean(false);
        run.setSubscription(new Disposable() {
            @Override public void dispose() { disposed.set(true); }
            @Override public boolean isDisposed() { return disposed.get(); }
        });

        StreamEvent ev = run.interrupt(client);

        assertEquals(OpenClawRun.Phase.INTERRUPTED, run.phase());
        assertTrue(client.cancelledRunIds.contains("r1"), "应 ACP cancel(r1)");
        assertTrue(disposed.get(), "应 dispose 上游订阅");
        assertEquals(EventType.SPEECH_INTERRUPTED, ev.getType());
        assertEquals("r1", ev.getData().get("runId"));
        assertEquals("barge-in", ev.getData().get("reason"));
    }

    @Test
    @DisplayName("cancelUpstream 幂等：runId 为空 / 订阅已 dispose 不报错")
    void cancelUpstreamIdempotent() {
        FakeOpenClawClient client = new FakeOpenClawClient();
        OpenClawRun run = new OpenClawRun("s1");   // 未设 runId / subscription
        assertDoesNotThrow(() -> run.cancelUpstream(client));
        assertTrue(client.cancelledRunIds.isEmpty(), "runId 为空时不应发 cancel");
    }
}
