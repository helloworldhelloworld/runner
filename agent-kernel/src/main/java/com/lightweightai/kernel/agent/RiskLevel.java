package com.lightweightai.kernel.agent;

/**
 * 工具风险等级 — 用于 byRiskLevel 等策略做分级过滤。
 *
 * 序:SAFE &lt; WRITE &lt; SYSTEM(按 ordinal 比较)。
 *
 * 使用场景:
 * - 主 agent 允许 SYSTEM(shell_exec 等),subagent 限到 WRITE
 * - 生产环境收紧到 SAFE,开发环境放开
 */
public enum RiskLevel {
    /** 只读,无副作用。如 search、read_file */
    SAFE,

    /** 会修改状态,但在 agent 自有 scope。如 write_file、send_email */
    WRITE,

    /** 能执行任意命令或 spawn 新 agent。如 shell_exec、spawn_subagent */
    SYSTEM
}
