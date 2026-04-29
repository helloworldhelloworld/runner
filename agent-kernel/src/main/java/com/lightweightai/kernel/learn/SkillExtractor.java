package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.prompt.Skill;

import java.util.Optional;

/**
 * 从 {@link Trajectory} 抽取 {@link Skill} 的策略接口 — Hermes 风格 self-improving loop 的核心 seam。
 *
 * 实现负责两件事:
 * 1. 判定该轨迹是否值得沉淀(返回 {@code Optional.empty()} 跳过)
 * 2. 若值得,组装 {@link Skill}(name / description / triggers / lazyBody)
 *
 * 默认实现:{@link HeuristicSkillExtractor}(纯启发式,零 LLM 调用)。
 * 高质量替代:LLM-judged 实现 — 用 LLM 评分 + 写 skill body,
 * 实现 {@code SkillExtractor} 即可,observer 不变。
 */
public interface SkillExtractor {

    Optional<Skill> extract(Trajectory trajectory);
}
