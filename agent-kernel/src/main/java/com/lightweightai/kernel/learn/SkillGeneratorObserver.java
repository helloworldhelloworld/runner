package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Hermes 风格 self-improving observer — 把成功完成的轨迹自动萃取成 {@link Skill}。
 *
 * 工作流:
 * <pre>
 * onAgentStart  → 重置状态,记录 userInput / sessionId
 * onPostToolUse → 累积 step(name, args, result)
 * onError       → 标记 errored=true(后续 complete 也不萃取)
 * onAgentComplete → 构造 Trajectory → 调 SkillExtractor.extract()
 *                  → 若 Some,Consumer<Skill>.accept(skill)(典型用法:engine.registerSkill)
 * </pre>
 *
 * <strong>线程模型</strong>:本观察者维护单条进行中的轨迹,适合"一次 AgentLoop 一个 observer 实例"
 * 的用法。AgentFactory 已经按 AgentProfile 创建独立 AgentLoop,所以每个 agent run 都是新实例。
 * 不要把同一实例同时挂在多个并行运行的 AgentLoop 上 — 步骤会串台。
 */
public final class SkillGeneratorObserver implements AgentObserver {

    private static final Logger logger = LoggerFactory.getLogger(SkillGeneratorObserver.class);

    private final SkillExtractor extractor;
    private final Consumer<Skill> registrar;

    private String userInput;
    private String sessionId;
    private final List<Trajectory.Step> steps = new ArrayList<>();
    private boolean errored;

    public SkillGeneratorObserver(SkillExtractor extractor, Consumer<Skill> registrar) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    @Override
    public void onAgentStart(String input, String sessionId) {
        this.userInput = input;
        this.sessionId = sessionId;
        this.steps.clear();
        this.errored = false;
    }

    @Override
    public void onPostToolUse(String toolName, Map<String, Object> args, ToolResult result) {
        steps.add(new Trajectory.Step(toolName, args, result));
    }

    @Override
    public void onError(Throwable error) {
        this.errored = true;
    }

    @Override
    public void onAgentComplete(AgentResponse response) {
        try {
            Trajectory trajectory = new Trajectory(
                    userInput != null ? userInput : "",
                    sessionId,
                    steps,
                    response != null ? response.getText() : null,
                    response != null ? response.getStopReason() : null,
                    errored);

            Optional<Skill> maybe = extractor.extract(trajectory);
            if (maybe.isPresent()) {
                registrar.accept(maybe.get());
                logger.info("Self-improve: registered new skill '{}' from session={}",
                        maybe.get().getName(), sessionId);
            }
        } catch (Exception e) {
            // 萃取失败不应阻塞 agent 主流程
            logger.warn("Self-improve extraction failed for session={}: {}", sessionId, e.getMessage());
        }
    }
}
