package com.lightweightai.openclaw.ws;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.openclaw.OpenClawChatHandler;
import com.lightweightai.openclaw.OpenClawChatRequest;
import com.lightweightai.openclaw.OpenClawEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成接缝测试（CLAUDE.md 规约）：真 {@link WebSocketOpenClawClient} ⟷ 忠实假对端
 * {@link FakeOpenClawServer}（真 RFC-6455 + OpenClaw chat 协议）。覆盖往返 + barge-in（含经 handler 端到端）
 * + 连接失败时序。不 mock 自己层、不连真 OpenClaw。
 */
@DisplayName("集成: WebSocketOpenClawClient ⟷ 假 OpenClaw 对端")
class WebSocketOpenClawRoundTripTest {

    @Test
    @DisplayName("往返：connect+chat.send 出站，chat:delta×N + chat:final 入站 → Token×N + RunEnd")
    void roundTripTokensThenFinal() throws Exception {
        try (FakeOpenClawServer server = new FakeOpenClawServer().reply("你好，", "我是小黄人。")) {
            WebSocketOpenClawClient client = new WebSocketOpenClawClient(server.url());

            List<OpenClawEvent> events = client.chat(new OpenClawChatRequest("s1", "minion", "hi"))
                    .collectList().block(Duration.ofSeconds(5));

            assertNotNull(events);
            List<String> tokens = events.stream()
                    .filter(e -> e instanceof OpenClawEvent.Token)
                    .map(e -> ((OpenClawEvent.Token) e).text()).toList();
            assertEquals(List.of("你好，", "我是小黄人。"), tokens);
            assertInstanceOf(OpenClawEvent.RunEnd.class, events.get(events.size() - 1));
            // 出站：connect + 带 sessionKey 的 chat.send
            assertTrue(server.received("\"method\":\"chat.send\""), "应发 chat.send");
            assertTrue(server.received("\"sessionKey\":\"s1\""), "chat.send 应带 sessionKey");
        }
    }

    @Test
    @DisplayName("barge-in：cancel(sessionId) 在该连接发 chat.abort{sessionKey}")
    void cancelSendsChatAbortBySessionKey() throws Exception {
        try (FakeOpenClawServer server = new FakeOpenClawServer().reply("从前有座山，").autoFinal(false)) {
            WebSocketOpenClawClient client = new WebSocketOpenClawClient(server.url());
            List<OpenClawEvent> got = new CopyOnWriteArrayList<>();
            Disposable sub = client.chat(new OpenClawChatRequest("s1", "minion", "讲故事")).subscribe(got::add);

            awaitUntil(() -> got.stream().anyMatch(e -> e instanceof OpenClawEvent.Token), 5000);
            client.cancel("s1");
            awaitUntil(() -> server.received("\"method\":\"chat.abort\""), 5000);
            assertTrue(server.received("\"sessionKey\":\"s1\""), "chat.abort 应带 sessionKey");
            sub.dispose();
        }
    }

    @Test
    @DisplayName("端到端经 handler：GatewayRequest → TEXT_DELTA；interrupt → speech_interrupted + 对端收 chat.abort")
    void endToEndBargeInThroughHandler() throws Exception {
        try (FakeOpenClawServer server = new FakeOpenClawServer().reply("从前").autoFinal(false)) {
            WebSocketOpenClawClient client = new WebSocketOpenClawClient(server.url());
            OpenClawChatHandler handler = new OpenClawChatHandler(client);

            List<StreamEvent> out = new CopyOnWriteArrayList<>();
            Disposable sub = handler.chatStreamReactive(
                    GatewayRequest.builder().sessionId("s1").message("讲故事").build()).subscribe(out::add);

            awaitUntil(() -> out.stream().anyMatch(e -> e.getType() == EventType.TEXT_DELTA), 5000);
            StreamEvent interrupted = handler.interrupt("s1");

            assertNotNull(interrupted);
            assertEquals(EventType.SPEECH_INTERRUPTED, interrupted.getType());
            awaitUntil(() -> server.received("\"method\":\"chat.abort\""), 5000);
            sub.dispose();
        }
    }

    @Test
    @DisplayName("连接失败快失败：错误端口 → 流 onError，不挂起")
    void connectFailureFailsFast() {
        WebSocketOpenClawClient client = new WebSocketOpenClawClient("ws://localhost:1/gateway");
        StepVerifier.create(client.chat(new OpenClawChatRequest("s1", "minion", "hi")))
                .expectError()
                .verify(Duration.ofSeconds(5));
    }

    /** 轮询等真异步 socket 往返的后置条件（集成测试，非虚拟时钟单测）。 */
    private static void awaitUntil(BooleanSupplier cond, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("条件在 " + timeoutMs + "ms 内未满足");
    }
}
