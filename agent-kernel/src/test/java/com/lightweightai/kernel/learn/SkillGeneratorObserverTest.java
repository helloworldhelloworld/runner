package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillGeneratorObserver — 边走边记 + 终态萃取")
class SkillGeneratorObserverTest {

    @Test
    @DisplayName("正常成功轨迹 → 抽取出 Skill 并交给 registrar")
    void successfulTrajectoryProducesSkill() {
        AtomicReference<Skill> registered = new AtomicReference<>();
        SkillGeneratorObserver observer = new SkillGeneratorObserver(
                new HeuristicSkillExtractor(),
                registered::set);

        observer.onAgentStart("帮我查 Tokyo 天气", "sess-1");
        observer.onPostToolUse("weather",
                Map.of("city", "Tokyo"),
                ToolResult.success("Sunny 25C"));
        observer.onAgentComplete(AgentResponse.builder()
                .text("Tokyo 现在晴 25 度").stopReason("end_turn").build());

        Skill skill = registered.get();
        assertNotNull(skill, "成功轨迹应产出 Skill");
        assertTrue(skill.getName().startsWith("auto-"),
                "默认启发式 name 应带 auto- 前缀: " + skill.getName());
        // body 是 lazy:取一次,验证含工具痕迹
        String body = skill.getSystemPrompt();
        assertTrue(body.contains("weather"), "body 应含工具名: " + body);
        assertTrue(body.contains("Tokyo"), "body 应含原始 args 内容: " + body);
    }

    @Test
    @DisplayName("错误轨迹 → 不萃取(extractor 拒绝)")
    void erroredTrajectoryProducesNoSkill() {
        AtomicReference<Skill> registered = new AtomicReference<>();
        SkillGeneratorObserver observer = new SkillGeneratorObserver(
                new HeuristicSkillExtractor(),
                registered::set);

        observer.onAgentStart("做点什么", "s");
        observer.onPostToolUse("flaky", Map.of(), ToolResult.error("boom"));
        observer.onAgentComplete(AgentResponse.builder()
                .text("失败了").stopReason("end_turn").build());

        assertNull(registered.get(), "tool 出错的轨迹不应产出 Skill");
    }

    @Test
    @DisplayName("空工具调用 → 不萃取(没有可学的解法)")
    void emptyTrajectoryProducesNoSkill() {
        AtomicReference<Skill> registered = new AtomicReference<>();
        SkillGeneratorObserver observer = new SkillGeneratorObserver(
                new HeuristicSkillExtractor(),
                registered::set);

        observer.onAgentStart("hi", "s");
        observer.onAgentComplete(AgentResponse.builder().text("你好").stopReason("end_turn").build());

        assertNull(registered.get(), "无 tool 调用的轨迹无可学,跳过");
    }

    @Test
    @DisplayName("onError → 标记为失败,即使后续 onAgentComplete 也不萃取")
    void errorMarksTrajectoryAsFailed() {
        AtomicReference<Skill> registered = new AtomicReference<>();
        SkillGeneratorObserver observer = new SkillGeneratorObserver(
                new HeuristicSkillExtractor(),
                registered::set);

        observer.onAgentStart("any", "s");
        observer.onPostToolUse("ok", Map.of(), ToolResult.success("ok"));
        observer.onError(new RuntimeException("network down"));
        observer.onAgentComplete(AgentResponse.builder()
                .text("partial").stopReason("end_turn").build());

        assertNull(registered.get(), "中途 onError 后即使 complete 也不萃取");
    }

    @Test
    @DisplayName("自定义 Extractor:总是萃取出固定 Skill — 证明 strategy seam 工作")
    void customExtractorIsHonored() {
        Skill fixed = Skill.builder().name("fixed").systemPrompt("FIXED").build();
        SkillExtractor always = trajectory -> Optional.of(fixed);

        AtomicReference<Skill> registered = new AtomicReference<>();
        SkillGeneratorObserver observer = new SkillGeneratorObserver(always, registered::set);

        observer.onAgentStart("anything", "s");
        observer.onAgentComplete(AgentResponse.builder().text("done").stopReason("end_turn").build());

        assertSame(fixed, registered.get(), "自定义 extractor 决定 → 应原样交给 registrar");
    }

    @Test
    @DisplayName("Trajectory 携带原始结构(Map args + ToolResult)")
    void trajectoryPreservesStructure() {
        AtomicReference<Trajectory> seen = new AtomicReference<>();
        SkillExtractor capture = trajectory -> {
            seen.set(trajectory);
            return Optional.empty();
        };

        SkillGeneratorObserver observer = new SkillGeneratorObserver(capture, s -> {});

        Map<String, Object> args = Map.of("k", 7, "v", List.of("a", "b"));
        ToolResult tr = ToolResult.success("the-result");

        observer.onAgentStart("the input", "ssn");
        observer.onPostToolUse("calc", args, tr);
        observer.onAgentComplete(AgentResponse.builder().text("done").stopReason("end_turn").build());

        Trajectory t = seen.get();
        assertNotNull(t);
        assertEquals("the input", t.userInput());
        assertEquals("ssn", t.sessionId());
        assertEquals(1, t.steps().size());
        assertEquals(args, t.steps().get(0).arguments(), "args 应是原 Map 引用副本");
        assertSame(tr, t.steps().get(0).result(), "result 应是原 ToolResult 引用");
    }
}
