package com.lightweightai.kernel.task.core;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * <b>Internal orchestration primitive. Not exposed to the LLM.</b>
 *
 * <p>Task 是系统内部编排单元。LLM 永远看不到 Task —— 它没有 schema、没有 description
 * 进入 prompt、不在 ToolRegistry 里。Task 与 {@link com.lightweightai.kernel.agent.Tool}
 * 是<b>不同本体</b>，命名空间独立、交互单向。</p>
 *
 * <p><b>这是认知边界，不是 trigger 边界。</b>区分标准是「模型是否需要感知它的存在」。
 * 不是「谁触发它」。同一段逻辑被代码触发还是被 LLM 触发，不影响它属于哪一侧 ——
 * 只有「模型是否需要感知」决定。</p>
 *
 * <h3>Task vs Tool（边界，权威定义）</h3>
 * <table>
 *   <tr><th></th><th>Task</th><th>Tool</th></tr>
 *   <tr><td>本体</td><td>系统内部编排单元</td><td>模型上下文的一部分</td></tr>
 *   <tr><td>模型可见性</td><td><b>不可见</b></td><td>可见（name/description/schema 进 prompt）</td></tr>
 *   <tr><td>调用者</td><td>编排器（TaskGraph、ChatHandler 装饰器、复合 Tool 内部）</td><td>LLM（function calling）</td></tr>
 *   <tr><td>输入</td><td>{@link TaskContext}（共享上下文）</td><td>args Map（LLM 生成的 JSON）</td></tr>
 *   <tr><td>输出</td><td>{@code Flux<StreamEvent>}</td><td>{@code ToolResult}</td></tr>
 *   <tr><td>描述写给谁</td><td>开发者</td><td>模型</td></tr>
 * </table>
 *
 * <h3>合法交互（单向）</h3>
 * <ol>
 *   <li>Task 调用 Tool（流水线节点中的一种，例如 RAG 检索 task 调一个 search tool）</li>
 *   <li>Tool 内部使用 TaskGraph（复合能力的内部编排）</li>
 *   <li>ChatHandler 装饰器编排 Tasks（pre/post-processing 流水线）</li>
 * </ol>
 *
 * <h3>明确禁止</h3>
 * <ul>
 *   <li>❌ 把 TaskGraph 反向包装成 Tool 给 LLM 调（无 TaskGraphTool）</li>
 *   <li>❌ {@code @ExposeAsTool} 一份代码两端用</li>
 *   <li>❌ ToolBinding / TaskBackedTool / TaskBackedToolSource 任何 Task→Tool 适配</li>
 *   <li>❌ TaskRegistry 与 ToolRegistry 桥接 —— 两套命名空间完全独立</li>
 * </ul>
 *
 * <h3>实现规范</h3>
 * <ul>
 *   <li>{@link #execute(TaskContext)} 必须是冷流（每次订阅都重新执行）。</li>
 *   <li>流中应至少 emit 一个 {@code TASK_COMPLETE} 事件携带 {@link TaskResult} payload；
 *       编排器从中提取并写入 {@link TaskContext#putResult}。</li>
 *   <li>不要在实现内 {@code .block()}：如需阻塞 IO，用 {@code subscribeOn(Schedulers.boundedElastic())}。</li>
 * </ul>
 *
 * <p>详细说明见 {@code docs/architecture/task-vs-tool.md}。</p>
 */
public interface Task {

    String getName();

    Flux<StreamEvent> execute(TaskContext context);
}
