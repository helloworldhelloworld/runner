# Task vs Tool 边界

> **一句话结论**：Task 是内部实现单元，Tool 是对外能力契约；它们是不同本体，
> 命名空间独立，交互单向。所有试图把两者「桥接」或「统一」的抽象都是在制造
> 新的复杂度去解决一个不存在的问题。

## 一、核心定位

|  | Task | Tool |
|---|---|---|
| **本体** | 系统内部编排单元 | 模型上下文的一部分 |
| **模型可见性** | 不可见 | 可见（name / description / schema 进 prompt） |
| **调用者** | 编排器（TaskGraph、ChatHandler 装饰器、复合 Tool 的内部实现） | LLM（function calling） |
| **输入** | `TaskContext`（共享上下文） | `args Map`（LLM 生成的 JSON） |
| **输出** | `Flux<StreamEvent>` | `ToolResult` |
| **描述写给谁看** | 开发者 | 模型 |

> **这是认知边界，不是 trigger 边界。** 区分标准是「模型是否需要感知它的存在」。

## 二、交互规则（单向）

合法形态只有三种：

1. **Task 调用 Tool** — 流水线节点的一种（例：RAG 检索 task 调一个 search tool）
2. **Tool 内部使用 TaskGraph** — 复合能力的内部编排（例：订票 Tool 内部是
   查余票 → 选班次 → 锁定 → 支付的 graph）
3. **ChatHandler 装饰器编排 Tasks** — pre/post-processing 流水线

### 明确禁止

- ❌ `TaskGraphTool` —— 把 graph 反向包装成 Tool 给 LLM 调
- ❌ `@ExposeAsTool` 注解 —— 一份代码两端用
- ❌ `ToolBinding` / `TaskBackedTool` / `TaskBackedToolSource` —— 任何 Task→Tool 适配
- ❌ `TaskRegistry` 与 `ToolRegistry` 的桥接 —— 两套命名空间完全独立

## 三、开发者决策表

| 谁需要"看到"这个能力的存在？ | 写法 |
|---|---|
| 模型 | Tool |
| 只有系统编排器 | Task |
| 两者都要 | 内部 service；分别包 Tool 和 Task 两层 |
| 复合 Tool 内部需要编排 | Tool，implementation 内 inject TaskGraph |

**复用原则**：复用在 service 层，不在能力契约层。Tool 的 schema/description
和 Task 的 TaskContext 输入空间，从 LLM 视角和系统视角看是不同的设计目标，
不应共用一套定义。

## 四、Task 体系内部原语

```
Task              ── 接口；纯异步；不暴露给模型
AbstractTask      ── 便捷基类；Flux.defer 包裹 doExecute 兜住同步异常
TaskContext       ── 共享上下文；ConcurrentHashMap + 真快照视图
                     happens-before 由 TaskGraph 调度顺序保证
TaskResult        ── SUCCESS/ERROR/SKIPPED + structured data + errorType + 栈摘要
JoinStrategy      ── 函数式接口 Predicate<List<TaskResult>>
                     预定义 ALL_SUCCESS / ANY_SUCCESS / ALL_COMPLETE /
                     FIRST_COMPLETE + quorum(k) 工厂；可自定义
TaskEvents        ── 强类型事件工厂；EventType.TASK_* enum；
                     taskError 保留 throwable 类型 + 栈摘要 + cause；
                     不再用 "task:" 魔法字符串
TaskRegistry      ── 内部 Task 注册表；与 ToolRegistry 完全独立
@TaskDef          ── SPI 自动注册标记；只面向 TaskRegistry
TaskGraph         ── DAG 执行器；显式 needs；levels；cancellation
Tasks             ── 便捷构造（sequential / parallel / fan-out-fan-in）
TaskGraphConfig   ── YAML DTO；FAIL_ON_UNKNOWN_PROPERTIES
TaskGraphLoader   ── YAML → TaskGraph；ref 在 TaskRegistry 解析
TaskGraphBundle   ── Loader 产物：graph + node-attributes
GuardExpression   ── 简易 guard DSL；解析失败抛错（不静默 fallback）
```

## 五、未解决的开放问题

下面这些点必须等真实场景驱动后再定型，强行写就是空架子：

1. **TaskGraph 调度策略**
   当前实现是 level-based BFS（同层并行，层间串行），简单可推理但有长尾阻塞。
   未来应改成事件驱动（每个 task 一旦满足前置即可执行）。
   触发条件：实测出现明显长尾。

2. **`TaskOrchestratingChatHandler` 三个契约**
   - pre-task 能否短路 AgentLoop（如安全审核拒答直接返回）？
   - pre-task 输出怎么注入到 prompt（改写 messages 还是写 TaskContext attribute
     由 prompt 拼装阶段读）？
   - post-task 能否改写 LLM 最终回复，还是只能旁路收集 telemetry？
   触发条件：第一个真实业务（如 SoulComfortAgent 安全检查）落地时定型。

3. **`TaskResultAggregator` 策略可配置性**
   聚合多个 TaskResult 喂给 LLM 时，截断 / 排序 / token budget 由谁决定？
   这是 ContextEngineering 的 ContextPlan 阶段，不能是死的 toString 拼接。
   触发条件：与 ContextCompactor / ContextPlan 设计一同决策。

4. **YAML DSL 依赖语法的扩展性**
   当前用 GitHub Actions 风格 `needs: [...]`。
   未来若需 conditional dependency（B 仅在 A 成功时才依赖 A），需要扩展。

5. **测试覆盖**
   - `TaskOrchestratingChatHandlerTest` —— 业务边界，最该测的
   - `TaskResultAggregatorTest` —— 业务最敏感
   - 上述两者的实现尚未落地，测试需同步补齐
   越靠近业务边界的类越没有测试，是 TDD 工作流的反信号，必须修正。

6. **战略层：参考实现**
   这套抽象需要至少一个真实落地场景做参考实现（如把现有 SoulComfortAgent 的
   某段 pre-processing 迁过来），否则接口形状容易在三个月后被业务推翻。

## 六、三个月后的回看检查清单

- [ ] 是否出现了任何 Task → Tool 的桥接代码？（应该一个都没有）
- [ ] `TaskRegistry` 与 `ToolRegistry` 是否仍然独立？是否有桥接 listener？
- [ ] 是否有 `StreamEvent.trace("task.*"...)` 残留？应该全部走 `EventType.TASK_*`
- [ ] 是否有 Task 实现里看见了硬编码的"先 A 后 B"逻辑没用 TaskGraph？
- [ ] 是否有 Tool 内部硬编码流水线没用 TaskGraph 编排？
- [ ] 是否有同一能力同时存在 `XxxTool` 和 `XxxTask` 但共享 service 层？
      若否，复用原则被违反。
- [ ] §五 的 6 个开放问题是否被场景驱动定型？还有哪些未解决？
