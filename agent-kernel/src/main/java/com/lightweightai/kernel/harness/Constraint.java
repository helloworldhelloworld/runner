package com.lightweightai.kernel.harness;

/**
 * 架构约束接口 (Harness Engineering - Architectural Constraints)。
 *
 * 约束是确定性护栏，在 Agent 执行的关键节点进行校验。
 * 约束结果有三个级别：PASS、WARN、BLOCK。
 */
public interface Constraint {

    /**
     * 约束名称
     */
    String getName();

    /**
     * 约束描述
     */
    String getDescription();

    /**
     * 执行约束检查
     *
     * @param input 待检查的输入（可以是用户消息、LLM 输出、工具调用参数等）
     * @return 检查结果
     */
    ConstraintResult check(String input);

    /**
     * 约束检查结果
     */
    record ConstraintResult(Level level, String message) {

        public enum Level {
            /** 通过 */
            PASS,
            /** 警告（继续执行但记录） */
            WARN,
            /** 阻断（终止执行） */
            BLOCK
        }

        public static ConstraintResult pass() {
            return new ConstraintResult(Level.PASS, null);
        }

        public static ConstraintResult warn(String message) {
            return new ConstraintResult(Level.WARN, message);
        }

        public static ConstraintResult block(String message) {
            return new ConstraintResult(Level.BLOCK, message);
        }

        public boolean isPassed() { return level == Level.PASS; }
        public boolean isWarning() { return level == Level.WARN; }
        public boolean isBlocked() { return level == Level.BLOCK; }
    }
}
