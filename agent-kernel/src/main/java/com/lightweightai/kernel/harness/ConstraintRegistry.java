package com.lightweightai.kernel.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 约束注册表 (Harness Engineering - Architectural Constraints)。
 *
 * 统一管理所有确定性护栏：
 * - 收编已有的 CrisisDetector、ContentFilter、maxIterations
 * - 新增 token-budget、tool-whitelist 等约束
 *
 * 提供分类检查方法：
 * - checkInput: 用户输入校验（预 LLM）
 * - checkOutput: LLM 输出校验（后 LLM）
 * - checkToolCall: 工具调用校验
 */
public class ConstraintRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ConstraintRegistry.class);

    private final Map<String, Constraint> inputConstraints = new ConcurrentHashMap<>();
    private final Map<String, Constraint> outputConstraints = new ConcurrentHashMap<>();
    private final Map<String, Constraint> toolConstraints = new ConcurrentHashMap<>();

    /**
     * 注册输入约束（预 LLM 检查）
     */
    public void registerInputConstraint(Constraint constraint) {
        inputConstraints.put(constraint.getName(), constraint);
        logger.info("Input constraint registered: {}", constraint.getName());
    }

    /**
     * 注册输出约束（后 LLM 检查）
     */
    public void registerOutputConstraint(Constraint constraint) {
        outputConstraints.put(constraint.getName(), constraint);
        logger.info("Output constraint registered: {}", constraint.getName());
    }

    /**
     * 注册工具调用约束
     */
    public void registerToolConstraint(Constraint constraint) {
        toolConstraints.put(constraint.getName(), constraint);
        logger.info("Tool constraint registered: {}", constraint.getName());
    }

    /**
     * 检查用户输入（预 LLM）
     * 返回第一个 BLOCK 或所有 WARN，无问题返回 PASS
     */
    public Constraint.ConstraintResult checkInput(String input) {
        return checkAll(inputConstraints.values(), input);
    }

    /**
     * 检查 LLM 输出（后 LLM）
     */
    public Constraint.ConstraintResult checkOutput(String output) {
        return checkAll(outputConstraints.values(), output);
    }

    /**
     * 检查工具调用
     */
    public Constraint.ConstraintResult checkToolCall(String toolCallInfo) {
        return checkAll(toolConstraints.values(), toolCallInfo);
    }

    /**
     * 获取所有已注册约束数量
     */
    public int size() {
        return inputConstraints.size() + outputConstraints.size() + toolConstraints.size();
    }

    /**
     * 获取所有约束名称（用于调试）
     */
    public List<String> getConstraintNames() {
        List<String> names = new ArrayList<>();
        inputConstraints.keySet().forEach(n -> names.add("input:" + n));
        outputConstraints.keySet().forEach(n -> names.add("output:" + n));
        toolConstraints.keySet().forEach(n -> names.add("tool:" + n));
        return Collections.unmodifiableList(names);
    }

    private Constraint.ConstraintResult checkAll(Iterable<Constraint> constraints, String input) {
        List<String> warnings = new ArrayList<>();
        for (Constraint constraint : constraints) {
            try {
                Constraint.ConstraintResult result = constraint.check(input);
                if (result.isBlocked()) {
                    logger.warn("Constraint BLOCKED by {}: {}", constraint.getName(), result.message());
                    return result;
                }
                if (result.isWarning()) {
                    logger.info("Constraint WARN from {}: {}", constraint.getName(), result.message());
                    warnings.add(constraint.getName() + ": " + result.message());
                }
            } catch (Exception e) {
                logger.error("Constraint {} failed with exception", constraint.getName(), e);
            }
        }
        if (!warnings.isEmpty()) {
            return Constraint.ConstraintResult.warn(String.join("; ", warnings));
        }
        return Constraint.ConstraintResult.pass();
    }
}
