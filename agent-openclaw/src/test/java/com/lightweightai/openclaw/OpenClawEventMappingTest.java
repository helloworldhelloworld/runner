package com.lightweightai.openclaw;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** OpenClawEvent → StreamEvent 映射细节（ADR-014）：工具事件默认隐藏 / 可选转 TRACE / 错误透传。 */
@DisplayName("OpenClawChatHandler - 事件映射")
class OpenClawEventMappingTest {

    private static GatewayRequest req() {
        return GatewayRequest.builder().sessionId("s1").message("hi").build();
    }

    @Test
    @DisplayName("默认隐藏 OpenClaw 工具事件（不外泄、不新增 EventType）")
    void toolEventsHiddenByDefault() {
        FakeOpenClawClient fake = new FakeOpenClawClient();
        OpenClawChatHandler handler = new OpenClawChatHandler(fake);   // forwardToolTrace=false
        List<StreamEvent> out = new CopyOnWriteArrayList<>();
        Disposable sub = handler.chatStreamReactive(req()).subscribe(out::add);

        fake.emit(new OpenClawEvent.RunStarted("r1"));
        fake.emit(new OpenClawEvent.ToolUse("look", Map.of()));
        fake.emit(new OpenClawEvent.Token("看到了"));
        fake.emit(new OpenClawEvent.ToolResult("look", "frame"));
        fake.emit(new OpenClawEvent.RunEnd("end_turn"));
        fake.complete();

        assertTrue(out.stream().noneMatch(e -> e.getType() == EventType.TRACE), "默认不应有 TRACE");
        assertEquals(List.of("看到了"), out.stream()
                .filter(e -> e.getType() == EventType.TEXT_DELTA).map(StreamEvent::getTextDelta).toList());
        sub.dispose();
    }

    @Test
    @DisplayName("forwardToolTrace=true 时工具/run 生命周期转既有 TRACE（观测，不新增 EventType）")
    void toolEventsForwardedAsTraceWhenEnabled() {
        FakeOpenClawClient fake = new FakeOpenClawClient();
        OpenClawChatHandler handler = new OpenClawChatHandler(fake, true);
        List<StreamEvent> out = new CopyOnWriteArrayList<>();
        Disposable sub = handler.chatStreamReactive(req()).subscribe(out::add);

        fake.emit(new OpenClawEvent.RunStarted("r1"));
        fake.emit(new OpenClawEvent.ToolUse("look", Map.of()));
        fake.emit(new OpenClawEvent.ToolResult("look", "frame"));
        fake.emit(new OpenClawEvent.RunEnd("end_turn"));
        fake.complete();

        // 断言恰好三条 TRACE,且各自内容/顺序对应 run.started / tool.use / tool.result（review #183-9,非仅计数）
        List<StreamEvent> traces = out.stream().filter(e -> e.getType() == EventType.TRACE).toList();
        assertEquals(3, traces.size(), "应恰好三条 TRACE");
        assertEquals("openclaw.run", traces.get(0).getTracePhase());
        assertTrue(traces.get(0).getTraceMessage().contains("started"), "第一条应为 run.started");
        assertEquals("openclaw.tool", traces.get(1).getTracePhase());
        assertTrue(traces.get(1).getTraceMessage().contains("use look"), "第二条应为 tool.use look");
        assertEquals("openclaw.tool", traces.get(2).getTracePhase());
        assertTrue(traces.get(2).getTraceMessage().contains("result look"), "第三条应为 tool.result look");
        sub.dispose();
    }

    @Test
    @DisplayName("ErrorEvent 透传为流错误")
    void errorEventPropagates() {
        FakeOpenClawClient fake = new FakeOpenClawClient();
        OpenClawChatHandler handler = new OpenClawChatHandler(fake);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Disposable sub = handler.chatStreamReactive(req()).subscribe(e -> {}, err::set);

        fake.emit(new OpenClawEvent.RunStarted("r1"));
        fake.emit(new OpenClawEvent.ErrorEvent(new IllegalStateException("openclaw boom")));

        assertNotNull(err.get());
        assertEquals("openclaw boom", err.get().getMessage());
        sub.dispose();
    }
}
