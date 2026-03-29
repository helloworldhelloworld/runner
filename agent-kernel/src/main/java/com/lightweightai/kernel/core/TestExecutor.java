package com.lightweightai.kernel.core;

import reactor.core.publisher.Flux;

/**
 * Skill 测试执行器接口
 *
 * 对真实 LLM 执行单个测试用例，返回流式事件。
 * 流包含：TEXT_DELTA, TOOL_CALL_START, TOOL_RESULT 等常规事件，
 * 以及最终的 POST_PROCESS_DATA("test_case_result", ...) 汇总事件。
 */
public interface TestExecutor {

    /**
     * 执行单个测试用例
     *
     * @param context 测试上下文（systemPrompt, 输入, mock 响应等）
     * @return 流式事件，最终包含测试结果汇总
     */
    Flux<StreamEvent> executeTestCase(SkillTestContext context);
}
