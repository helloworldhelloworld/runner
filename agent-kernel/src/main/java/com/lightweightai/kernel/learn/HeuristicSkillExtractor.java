package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.prompt.Skill;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 默认启发式 SkillExtractor — 零 LLM 调用,纯规则:
 *
 * 跳过:
 * - 中途出错({@code errored})
 * - 没有任何 tool 调用
 * - 任一 step 出错
 * - {@code stopReason} 不是 {@code end_turn}(没正常收尾)
 *
 * 否则生成:
 * - {@code name}: {@code auto-<userInput-hash>}
 * - {@code description}: 基于 userInput 的简介
 * - {@code triggers}: userInput 整句作为触发词(下次类似输入命中)
 * - {@code lazyBody}: 渲染轨迹为 markdown 大纲(延迟到首次调用 getSystemPrompt 才执行)
 */
public final class HeuristicSkillExtractor implements SkillExtractor {

    @Override
    public Optional<Skill> extract(Trajectory t) {
        if (t.errored()) return Optional.empty();
        if (t.steps().isEmpty()) return Optional.empty();
        if (t.steps().stream().anyMatch(Trajectory.Step::isError)) return Optional.empty();
        if (!"end_turn".equals(t.stopReason())) return Optional.empty();

        String name = "auto-" + Integer.toHexString(t.userInput().hashCode() & 0x7fffffff);
        String description = "auto-extracted from successful run: "
                + truncate(t.userInput(), 60);

        Skill skill = Skill.builder()
                .name(name)
                .description(description)
                .addTrigger(t.userInput())
                .lazyBody(() -> renderBody(t))
                .priority(200)  // 低优先级:让人工 skill 排在前面
                .build();

        return Optional.of(skill);
    }

    private String renderBody(Trajectory t) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Past successful approach\n\n")
                .append("**Original request**: ").append(t.userInput()).append("\n\n")
                .append("**Step sequence** (").append(t.steps().size()).append(" tool call")
                .append(t.steps().size() == 1 ? "" : "s").append("):\n\n");

        int i = 1;
        for (Trajectory.Step step : t.steps()) {
            sb.append(i++).append(". `").append(step.toolName()).append("`(")
                    .append(formatArgs(step.arguments())).append(")")
                    .append(" → ").append(truncate(step.result().getContent(), 120))
                    .append("\n");
        }

        if (t.finalText() != null && !t.finalText().isBlank()) {
            sb.append("\n**Final answer**: ").append(truncate(t.finalText(), 200)).append("\n");
        }
        sb.append("\nWhen you see a similar request, prefer this exact tool sequence.\n");
        return sb.toString();
    }

    private static String formatArgs(java.util.Map<String, Object> args) {
        if (args.isEmpty()) return "";
        return args.entrySet().stream()
                .map(e -> e.getKey() + "=" + truncate(String.valueOf(e.getValue()), 40))
                .collect(Collectors.joining(", "));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    @Override
    public String toString() {
        return "HeuristicSkillExtractor[stopReason=end_turn, no-error, has-steps]"
                .toLowerCase(Locale.ROOT);
    }
}
