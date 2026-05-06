package com.lightweightai.kernel.task.core;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * 编排单元。
 *
 * <h3>Task vs Tool（关键边界）</h3>
 * <p>本仓库同时存在 {@link com.lightweightai.kernel.agent.Tool} 与 Task。两者并非冗余：</p>
 * <ul>
 *   <li><b>Tool</b>：面向 <i>LLM 自主调用</i>。LLM 看 schema、决定何时调、传入 {@code Map<String,Object>} args，
 *       本质是「让模型在推理过程中触发副作用」。</li>
 *   <li><b>Task</b>：面向 <i>编排器（DAG / pipeline）调用</i>。调用方是代码，
 *       传入共享 {@link TaskContext}，可消费上游任务结果。本质是「在确定性流程中编排步骤」。</li>
 * </ul>
 *
 * <p>两者通过 {@link com.lightweightai.kernel.task.integration.TaskGraphTool} 桥接：
 * 一张 TaskGraph 可被打包成 Tool 暴露给 LLM。<b>判断标准</b>：是否需要 LLM 在推理时自主决定调用？
 * 是 → Tool；否（属于固定流程）→ Task；既要又要 → 写成 Task，再用 TaskGraphTool 暴露。</p>
 *
 * <h3>实现规范</h3>
 * <ul>
 *   <li>{@link #execute(TaskContext)} 必须是冷流（每次订阅都重新执行）。</li>
 *   <li>流中应至少 emit 一个 {@code TASK_COMPLETE} 事件携带 {@link TaskResult} payload；
 *       编排器从中提取并写入 {@link TaskContext#putResult}。</li>
 *   <li>不要在实现内 {@code .block()}：如需阻塞 IO，用 {@code subscribeOn(Schedulers.boundedElastic())}。</li>
 * </ul>
 */
public interface Task {

    String getName();

    Flux<StreamEvent> execute(TaskContext context);
}
