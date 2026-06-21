package com.lightweightai.openclaw;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenClaw 完整大脑适配（ADR-014）acceptance —— 用 {@link FakeOpenClawClient} 端到端驱动
 * {@link OpenClawChatHandler}，证：(1) token 流→TEXT_DELTA + RunEnd→LLM_COMPLETE；
 * (2) 下游语音平面（SpeakableChunkProcessor）照常出 SPEAKABLE_CHUNK；
 * (3) barge-in 取消 OpenClaw run 并发 SPEECH_INTERRUPTED。不依赖真 OpenClaw。
 */
@DisplayName("OpenClawChatHandler - 完整大脑适配 acceptance")
class OpenClawChatHandlerAcceptanceTest {

    private static GatewayRequest req(String sessionId, String message) {
        return GatewayRequest.builder().sessionId(sessionId).message(message).build();
    }

    @Test
    @DisplayName("token 流映射为 TEXT_DELTA；RunEnd 映射为 LLM_COMPLETE；上游完成则流完成")
    void tokensMapToTextDeltaThenComplete() {
        FakeOpenClawClient fake = new FakeOpenClawClient();
        OpenClawChatHandler handler = new OpenClawChatHandler(fake);

        List<StreamEvent> out = new CopyOnWriteArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        Disposable sub = handler.chatStreamReactive(req("s1", "你好"))
                .subscribe(out::add, e -> {}, () -> completed.set(true));

        fake.emit(new OpenClawEvent.RunStarted("r1"));
        fake.emit(new OpenClawEvent.Token("你好，"));
        fake.emit(new OpenClawEvent.Token("我是小黄人。"));
        fake.emit(new OpenClawEvent.RunEnd("end_turn"));
        fake.complete();

        // 入站映射：message 透传给 OpenClaw；agentId 缺省兜 "minion"
        assertEquals("你好", fake.lastRequest.message());
        assertEquals("minion", fake.lastRequest.agentId());

        List<String> deltas = out.stream()
                .filter(e -> e.getType() == EventType.TEXT_DELTA)
                .map(StreamEvent::getTextDelta).toList();
        assertEquals(List.of("你好，", "我是小黄人。"), deltas);
        assertEquals(1, out.stream().filter(e -> e.getType() == EventType.LLM_COMPLETE).count());
        assertTrue(completed.get(), "上游 onComplete 后流应完成");
        sub.dispose();
    }

    @Test
    @DisplayName("下游 SpeakableChunkProcessor 仍能从 OpenClaw token 流切出 SPEAKABLE_CHUNK")
    void downstreamSpeakableChunkStillWorks() {
        FakeOpenClawClient fake = new FakeOpenClawClient();
        OpenClawChatHandler handler = new OpenClawChatHandler(fake);

        List<StreamEvent> out = new CopyOnWriteArrayList<>();
        Disposable sub = handler.chatStreamReactive(req("s1", "hi"))
                .transform(new SpeakableChunkProcessor())   // 语音平面后处理器，与脑实现无关
                .subscribe(out::add);

        fake.emit(new OpenClawEvent.RunStarted("r1"));
        fake.emit(new OpenClawEvent.Token("你好，"));
        fake.emit(new OpenClawEvent.Token("我是小黄人。"));
        fake.emit(new OpenClawEvent.RunEnd("end_turn"));
        fake.complete();

        List<String> chunks = out.stream()
                .filter(e -> e.getType() == EventType.SPEAKABLE_CHUNK)
                .map(e -> (String) e.getData().get("text")).toList();
        assertEquals(List.of("你好，我是小黄人。"), chunks);
        sub.dispose();
    }

    @Test
    @DisplayName("barge-in：interrupt 取消 OpenClaw run + 发 SPEECH_INTERRUPTED + 停转发")
    void bargeInCancelsAndEmitsSpeechInterrupted() {
        FakeOpenClawClient fake = new FakeOpenClawClient();
        OpenClawChatHandler handler = new OpenClawChatHandler(fake);

        List<StreamEvent> out = new CopyOnWriteArrayList<>();
        Disposable sub = handler.chatStreamReactive(req("s1", "讲个长故事")).subscribe(out::add);

        fake.emit(new OpenClawEvent.RunStarted("r1"));
        fake.emit(new OpenClawEvent.Token("从前有座山，"));
        int before = out.size();

        StreamEvent interrupted = handler.interrupt("s1");

        // (a) 返回 SPEECH_INTERRUPTED，带 runId + reason
        assertNotNull(interrupted);
        assertEquals(EventType.SPEECH_INTERRUPTED, interrupted.getType());
        assertEquals("r1", interrupted.getData().get("runId"));
        assertEquals("barge-in", interrupted.getData().get("reason"));
        // (b) ACP cancel 真发给 OpenClaw（按 sessionKey，chat.abort）
        assertTrue(fake.cancelled.contains("s1"), "应向 OpenClaw 发 cancel(sessionId=s1)");
        // (c) 在途订阅已 dispose：后续 OpenClaw 事件不再到达下游
        fake.emit(new OpenClawEvent.Token("继续讲……"));
        assertEquals(before, out.size(), "打断后不应再转发 OpenClaw token");
        sub.dispose();
    }

    @Test
    @DisplayName("无在途 run 时 interrupt 返回 null")
    void interruptNoActiveRunReturnsNull() {
        FakeOpenClawClient fake = new FakeOpenClawClient();
        OpenClawChatHandler handler = new OpenClawChatHandler(fake);
        assertNull(handler.interrupt("nope"));
        assertTrue(fake.cancelled.isEmpty());
    }
}
