package com.lightweightai.kernel.prompt;

import java.util.function.Predicate;

/**
 * 模块化 Prompt 片段
 *
 * 参考 Claude Code 泄露源码的 110+ 独立 prompt 字符串条件组装模型：
 * 每个 PromptSection 是一个可条件激活的 prompt 片段。
 *
 * 用法：
 *   PromptSection.of("identity", 0, "You are a helpful assistant.")
 *   PromptSection.conditional("plan-mode", 10, ctx -> ctx.isMode("plan"), "Plan instructions...")
 */
public class PromptSection {

    private final String id;
    private final int order;
    private final String content;
    private final Predicate<PromptRequest> condition;

    private PromptSection(String id, int order, String content, Predicate<PromptRequest> condition) {
        this.id = id;
        this.order = order;
        this.content = content;
        this.condition = condition;
    }

    /**
     * 无条件片段（始终生效）
     */
    public static PromptSection of(String id, int order, String content) {
        return new PromptSection(id, order, content, req -> true);
    }

    /**
     * 条件片段（满足条件时才注入）
     */
    public static PromptSection conditional(String id, int order,
                                             Predicate<PromptRequest> condition, String content) {
        return new PromptSection(id, order, content, condition);
    }

    /**
     * 判断在给定请求下是否激活
     */
    public boolean isActive(PromptRequest request) {
        return condition.test(request);
    }

    public String getId() { return id; }
    public int getOrder() { return order; }
    public String getContent() { return content; }
}
