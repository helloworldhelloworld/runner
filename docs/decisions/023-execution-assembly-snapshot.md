# ADR-023：执行装配快照与实际消费

- 日期：2026-09-07
- 状态：Accepted
- 前置：ADR-018—022

## 问题

模型选择已固定，但 ToolCallingLoop 仍通过可变注册表查找工具，定义与实际实现可能属于不同版本。
设备工具定义也可能来自默认实现。native AgentLoop 没有消费管理端的 PromptEngine，缺少统一的装配观察端口。

## 决策

1. 增加执行配置作用域，随 ToolExecutionContext 传递。同源提示目录及共享工具目录在执行家族中
   捕获一次；局部任务控制保持各自绑定。新根运行和接续获得新作用域，普通直接调用保持兼容入口。
2. ToolRegistry 增加快照入口；按实际 DeviceContext 固定工具实现，深拷贝其模型定义。
   ScopedToolRegistry 先固定父目录再叠加局部控制。执行仍通过原注册表检查实时禁用、撤销和参数策略；
   快照不会恢复已经被撤销的权限或断开的设备。现有 Tool/ToolSourceProvider/MCP 方法不变。
3. 增加 PromptSource/PromptSnapshot，PromptEngine 提供线程安全的内容快照。native 由实际管理端
   同一来源获得技能内容，按输入触发；无触发词的已发布技能为常驻片段。人格仍由 AgentProfile 提供，
   不把 Soul 专用基础提示无条件加给 Minion。根任务与子任务各自选择片段，但使用同一目录内容版本。
4. AgentLoop 在执行开始时产生不可变装配清单：Profile/人格、选中提示与内容版本、实际 Provider、
   LLM 参数、设备及工具定义/实现标识。Provider、模型调用与 ToolExecutor 消费同一次装配结果。
   AgentObserver 通过新增默认方法接收清单，不用 TRACE 字符串或私有事件冒充协议。
5. JSON 配置采用确定性内容摘要，拒绝非 JSON 值和非有限数值；LLMOptions 通过新增快照入口深拷贝。
   运行清单描述本轮真实选择，不把任意 Java 工具内部状态或外部模型服务版本宣称为可序列化。
   源码/构建与依赖证据仍由 ADR-021 的候选包绑定；完整配置发布校验是后续独立步骤。

## 验收

先复现模型看到旧工具定义但调用了新实现，以及已注册提示没有进入 native 请求。
随后验证真实 Spring/Provider 请求、同源子任务、设备专用 schema、运行中撤销、新轮/接续更新、
深层配置变更隔离及观察清单摘要。保留 ADR-018—022 的取消、权限、设备与候选验收。
