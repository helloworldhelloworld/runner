# ADR 005: Task vs Tool 边界

## 背景

PR #109 引入 `Task` / `TaskGraph` / `TaskGraphTool`，与已有的 `Tool` / `ToolRegistry`
形成了两套并行抽象。如果不显式定义边界，三个月后会有人问"同一个能力（如知识检索）
该写成 RetrievalTool 还是 RetrievalTask？"，这种概念漂移会让两套抽象都退化成杂物。

## 决策

按四个判断轴定义 Task 与 Tool：

| 轴 | Tool | Task |
|---|---|---|
| **决策者** | LLM 在推理中自主选 | 代码（编排器）固定调度 |
| **确定性** | 自适应 | 确定性（builder/YAML 预声明） |
| **入参形态** | `Map<String,Object>`（schema 验证） | `TaskContext`（按 key 读上游 typed result） |
| **组合方式** | 模型在推理循环里组合 | 代码用 DAG（needs / join / guard） |

## 判断矩阵

1. 需要 LLM 在推理时自主决定何时调？→ **Tool**
2. 需要表达"A → B → C，B 读 A 的输出"？→ **Task**
3. 同一能力两边都要？→ **Task + `TaskGraphTool` 包装**，禁止双重实现
4. 能力只被一处调用一次、无上下游？→ **普通方法**，两者皆不需要

## 反模式

- ❌ Tool 内部硬编码一段固定流水线 → 提取为 Task
- ❌ 把每个 Task 自动暴露为 Tool → 污染 LLM tool list；只在「LLM 真的需要在多个 pipeline 间选」才暴露
- ❌ Task 接口加 `executeSync()` default `.block()` → 鼓励同步调用，会让"代码组合"扩散到调用栈深处
- ❌ 用 `StreamEvent.trace("task.start", ...)` 字符串约定派发任务事件 → 必须用 `EventType.TASK_*` enum + 强类型 payload

## 升级 / 降级路径

- **Task → Tool（升级）**：固定流水线变得"有时候让模型选着调"时，加一个 `TaskGraphTool` wrapper，
  保持 Task 本体不动。
- **Tool → Task（降级）**：发现 LLM 老是按固定顺序调用一组 Tool 时，把那段固定逻辑提到 Task，
  Tool 退化为该 Task 的"启动器"。

## 注册表分离的理由

`TaskRegistry` 与 `ToolRegistry` 不合并：

- 调用方不同（编排代码 vs LLM）
- schema 形态不同（TaskContext 类型化读 vs JSON Schema 描述）
- 发现来源不同（@TaskDef SPI vs @ClientTool / MCP / 注解）
- 合并会导致 `ToolRegistry.getToolDefinitions()` 把不该暴露给 LLM 的内部 Task 也吐出来

桥接通过 `TaskGraphTool`：单向（Task → Tool），不反向。

## 三个月后的回看检查清单

- [ ] 是否有 Tool 实现里看见了硬编码的"先 A 后 B"流水线？→ 重构为 Task
- [ ] 是否有 Task 只被一处一次调用、且无 needs/guard/join？→ 删除，改为普通方法
- [ ] 是否有同一能力同时存在 `XxxTool` 和 `XxxTask`？→ 必须是 Task + TaskGraphTool 桥接
- [ ] 是否有 `StreamEvent.trace("task.*"...)` 残留？→ 必须迁到 `EventType.TASK_*`
- [ ] `getToolDefinitions()` 是否包含了 LLM 不应看到的内部 Task？→ 检查 `expose-as-tools` 声明
- [ ] 是否有 `TaskGraphTool` 在 reactor 线程里被 `block()`？→ 必须 `subscribeOn(boundedElastic)`

## 暂未实装、待场景驱动

下面这些类故意不在本次实现：

- `TaskOrchestratingChatHandler` — pre/post 语义（短路 agent loop？改写 messages 还是 attribute？
  改写最终回复还是只 telemetry？）需要至少一个真实场景（如 SoulComfortAgent 的安全检查）
  捏过一遍才能定型。强行写就是空架子。
- `TaskResultAggregator` — token budget / priority / 截断策略 应纳入 ContextPlan / ContextCompactor
  统一治理，不应在 task 层独立实现。
