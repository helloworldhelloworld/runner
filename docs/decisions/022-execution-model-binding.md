# ADR-022：一次执行及其子任务固定模型绑定

- 日期：2026-09-06
- 状态：Accepted（执行级模型选择已实现，真实 Spring/MVC/HTTP 验收通过）
- 前置：[ADR-017](017-versioned-harness-improvement-loop.md)、[ADR-020](020-foreground-subagent-cancellation.md)、[ADR-021](021-frozen-candidate-evidence-bundle.md)

## 问题

DynamicLLMProvider 在每次调用时读取全局 delegate。管理端切换模型后，已开始的一轮工具循环
及其随后创建的子任务可能使用不同模型。冻结源码的验收包没有解决这个生产执行边界。

## 决策

1. kernel 新增可选 ExecutionScopedLLMProvider SPI。绑定操作返回当前执行使用的稳定 Provider；
   普通 LLMProvider 保持原有接口与实例行为。DynamicLLMProvider 原子读取一次 delegate；
   ResilientLLMProvider 保留重试装饰，不通过拆掉包装器获得固定模型。
2. ExecutionModelScope 按配置源对象身份缓存绑定，通过 ToolExecutionContext 传递给子任务。
   同一任务树使用同一个配置源时共享绑定；不同配置源独立绑定，不按模型名称混为一体。
   scope 只固定模型选择，不冻结取消信号、实时权限或设备可用性。
3. native 根执行在开始时创建新 scope；响应式执行在订阅时绑定。一次工具循环所有迭代使用
   同一 Provider；独立新执行和隐式 interrupt/resume 创建新 scope，即使复用 AgentLoop。
   子任务继承父 scope，父任务正常完成后继续运行的子任务仍保留原绑定。
4. ToolCallingLoop 的同步、异步和 reactive 独立入口也在执行边界绑定，不能仅修复 web Factory。
   不改变现有公开方法签名；调用者显式传入已有 scope 表示继承该执行的配置。
5. 管理端仍先成功构造新 Provider 再切换；配置构造失败不改变当前绑定源。旧执行仍持有旧 Provider，
   不在切换时关闭它。现有 Provider 没有统一释放端口，连接池治理另行设计。

## 验收与边界

真实 Spring 装配 native Orchestrator；真实 MVC 管理请求修改模型；真实 Provider 与重试包装器
对接本地 HTTP/SSE 对端。控制工具完成时刻，在模型切换前后断言实际出站 model、工具定义、参数、
工具回包及子任务输入。覆盖同步/reactive、并发新执行、接续、订阅时机和失败更新。
模型切换的同步/reactive 场景可导出受控 HTTP 请求和工具实参，使用 ADR-021 的冻结候选执行器
单独重跑；新验收定义不改变已有取消链样本的评测内容。

这是生产装配快照的第一步，尚未交付完整 HarnessSnapshot、模型配置持久化/发布摘要校验、
Profile.modelOverride 路由、Tool 实现冻结、Skill 发布消费、在线轨迹采集或自动优化。
后续须把这些配置共同绑定到可审计的运行清单，再接发布和下一轮真实请求的核对。

## 实施证据

修改前 23:30:19 全量 clean 基线通过；两个真实 HTTP/SSE 失败场景复现同一轮从 v1 切到 v2。
修复后 5 项真实装配验收通过；2026-09-06 23:50:10 全量 clean test：2303 项，2283 通过、
20 跳过，0 失败/错误，13 模块成功。Python 网关往返探针显式启用并通过。
23:52:05，补充证据导出后的冻结副本 clean 验收 25 项通过，2 份实际 HTTP/工具证据绑定校验通过。
独立样本：[验收定义](../../evals/execution-model-binding.json)、[使用命令](../guides/improvement-loop.md)。
后续验证与续接边界统一记录到[实施进展](../design/runner-minion-system-evolution.md#124-2026-09-06-实施进展与续接点)。
