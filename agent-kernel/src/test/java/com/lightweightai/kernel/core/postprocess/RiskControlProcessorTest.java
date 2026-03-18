package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RiskControlProcessor 测试")
class RiskControlProcessorTest {

    @Test
    @DisplayName("安全内容正常透传")
    void shouldPassThroughSafeContent() {
        RiskControlProcessor processor = new RiskControlProcessor(
                text -> RiskControlProcessor.RiskResult.safe(),
                1  // 每个 token 都检查
        );

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("Hello "),
                StreamEvent.textDelta("safe world")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(StreamEvent.EventType.TEXT_DELTA, result.get(0).getType());
        assertEquals("Hello ", result.get(0).getTextDelta());
        assertEquals(StreamEvent.EventType.TEXT_DELTA, result.get(1).getType());
        assertEquals("safe world", result.get(1).getTextDelta());
    }

    @Test
    @DisplayName("warning 时注入警告事件并继续")
    void shouldInjectWarningAndContinue() {
        RiskControlProcessor processor = new RiskControlProcessor(
                text -> text.contains("risky")
                        ? RiskControlProcessor.RiskResult.warning("mild risk detected")
                        : RiskControlProcessor.RiskResult.safe(),
                1
        );

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("This is risky content")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);

        // 应有原始事件 + 警告事件
        assertEquals(2, result.size());
        assertEquals(StreamEvent.EventType.TEXT_DELTA, result.get(0).getType());
        assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, result.get(1).getType());
        assertEquals("risk_control", result.get(1).getCategory());
        assertEquals("warning", result.get(1).getData().get("action"));
        assertEquals("mild risk detected", result.get(1).getData().get("reason"));
    }

    @Test
    @DisplayName("blocked 时注入错误事件并终止流")
    void shouldBlockOnHighRisk() {
        RiskControlProcessor processor = new RiskControlProcessor(
                text -> text.contains("dangerous")
                        ? RiskControlProcessor.RiskResult.blocked("harmful content")
                        : RiskControlProcessor.RiskResult.safe(),
                1
        );

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("This is dangerous content"),
                StreamEvent.textDelta("should not reach here")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);

        // 第一个事件触发 blocked: POST_PROCESS_DATA + ERROR
        // 第二个事件不应出现（但 concatMap 可能仍会处理后续元素）
        assertTrue(result.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.POST_PROCESS_DATA
                        && "blocked".equals(e.getData().get("action"))));
        assertTrue(result.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.ERROR
                        && e.getError() instanceof RiskControlProcessor.RiskControlException));
    }

    @Test
    @DisplayName("非 TEXT_DELTA 事件透传")
    void shouldPassThroughNonTextEvents() {
        RiskControlProcessor processor = new RiskControlProcessor(
                text -> RiskControlProcessor.RiskResult.safe(),
                1
        );

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.toolCallStart(null),
                StreamEvent.textDelta("safe")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(StreamEvent.EventType.TOOL_CALL_START, result.get(0).getType());
        assertEquals(StreamEvent.EventType.TEXT_DELTA, result.get(1).getType());
    }

    @Test
    @DisplayName("按 token 间隔触发检查")
    void shouldCheckAtTokenIntervals() {
        int[] checkCount = {0};

        RiskControlProcessor processor = new RiskControlProcessor(
                text -> {
                    checkCount[0]++;
                    return RiskControlProcessor.RiskResult.safe();
                },
                100  // 高阈值，短文本不会触发
        );

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("hi"),   // ~1 token, < 100
                StreamEvent.textDelta("there") // ~2 tokens, still < 100
        );

        input.transform(processor).collectList().block();

        // 不应触发检查（token 数未达阈值）
        assertEquals(0, checkCount[0]);
    }

    @Test
    @DisplayName("estimateTokens 估算正确")
    void shouldEstimateTokens() {
        assertEquals(1, RiskControlProcessor.estimateTokens("hi"));
        assertEquals(0, RiskControlProcessor.estimateTokens(""));
        assertEquals(0, RiskControlProcessor.estimateTokens(null));
        assertTrue(RiskControlProcessor.estimateTokens("a longer text for estimation") > 1);
    }

    @Test
    @DisplayName("处理器名称和优先级正确")
    void shouldHaveCorrectNameAndOrder() {
        RiskControlProcessor processor = new RiskControlProcessor(
                text -> RiskControlProcessor.RiskResult.safe(), 50);
        assertEquals("risk-control", processor.getName());
        assertEquals(10, processor.getOrder());
    }

    @Test
    @DisplayName("RiskResult 状态正确")
    void riskResultShouldHaveCorrectStates() {
        RiskControlProcessor.RiskResult safe = RiskControlProcessor.RiskResult.safe();
        assertTrue(safe.isSafe());
        assertFalse(safe.hasWarning());
        assertFalse(safe.isBlocked());
        assertNull(safe.getReason());

        RiskControlProcessor.RiskResult warning = RiskControlProcessor.RiskResult.warning("warn");
        assertFalse(warning.isSafe());
        assertTrue(warning.hasWarning());
        assertFalse(warning.isBlocked());
        assertEquals("warn", warning.getReason());

        RiskControlProcessor.RiskResult blocked = RiskControlProcessor.RiskResult.blocked("block");
        assertFalse(blocked.isSafe());
        assertFalse(blocked.hasWarning());
        assertTrue(blocked.isBlocked());
        assertEquals("block", blocked.getReason());
    }
}
