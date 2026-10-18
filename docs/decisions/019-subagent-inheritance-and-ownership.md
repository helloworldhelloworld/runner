# ADR-019：子任务上下文、权限继承与所有权

- 日期：2026-09-06
- 状态：Accepted（工作区实现与自动化验收完成）
- 前置：[ADR-018](018-request-tool-execution-scope.md)
- 实施入口：[全局方案 §12.4](../design/runner-minion-system-evolution.md#124-2026-09-06-实施进展与续接点)

## 问题

native spawn 已经局部绑定，但 SubagentRuntime 只按目标 Profile 创建子 Agent，丢失父权限上限和
ToolExecutionContext。全局 wait/list 允许一个会话消费另一会话的任务结果。目标 Profile 的
maxSpawnDepth 也被当作“是否可被创建”的判断，导致不能继续 spawn 的叶子 Agent 无法承担任务。
运行状态取消后仍可被迟到的完成覆盖，等待采用轮询且完成缓存存在先移除再写入的空窗。

## 决策

1. native root 和 child 通过同一个运行装配方法获得局部 spawn/wait/list/stop 控制工具。
   spawn 携带发起调用时的 ToolExecutionContext；后台子任务使用同一 dispatcher、设备类型/版本和超时。
   这些服务来自服务器绑定，模型参数不能替换父上下文或权限。
   Spring 的 native 装配不再往全局目录注册旧 wait/list 工具；旧构造器仅供显式兼容调用使用。
   控制工具遵守已有 Tool 风险约定：spawn 为 SYSTEM，stop 为 WRITE，wait/list 为 SAFE；
   只读或 WRITE 上限不能因为 spawn 未标记风险而创建后台任务。
2. 每一代的有效策略为所有祖先策略与当前 Profile 策略的交集，任一 DENY 都拒绝；ABSTAIN 不移除
   祖先限制。祖先策略仍使用各自原始 Profile 求值，执行阶段使用当前调用的实际参数与上下文。
   保留 ToolPolicy.chain 的“首个非 ABSTAIN”语义，交集使用独立的组合方式。
3. native spawn 深度按父任务剩余层数逐代递减，并被子 Profile 进一步收紧。子 Profile 的 0
   表示叶子，可以被创建，但没有 spawn 能力；选择宽松 Profile 不能恢复耗尽的父层数。
4. 局部 wait/list/stop 仅访问当前服务器绑定作用域创建的直接子任务。作用域由根 session key
   和独立的子任务 ID 路径构成，不从拼接后的 session key 反向解析；即使客户端把 sessionId 写成
   子任务路径，也不能取得该子任务的控制权。未知任务和无权访问的任务使用相同结果。
   等待前先检查所有目标；子任务不能查看兄弟任务。
   stop 从被授权子任务沿实际父子关系级联；父任务或中间任务已经完成并离开 active 表时，
   也可停止尚未结束的后代，不跨入 session 文本相同的另一根作用域。
5. 终态单向变化；取消早于 Future 绑定时，迟到 Future 也取消。等待使用完成信号替代 sleep 轮询；
   完成结果先进入缓存再离开 active 表。注册后构建/提交失败释放名额；取消与子任务注册使用同一临界区，
   防止已停止的父任务再次产生后代。
6. 当前子任务状态作为本轮工具执行的额外检查：取消后迟到的模型工具调用不能产生副作用。
   该状态检查不向后代继承；祖先正常完成不能令独立运行的后代任务失去工具能力。
7. 默认 InMemoryProvider 的列表由父子任务共享访问，读写采用同一实例锁，防止摘要写入与检索
   并发导致 ConcurrentModificationException 或丢失记录。此改动保证内存操作一致性，不提供认证隔离或持久化。

## 兼容与边界

Tool、ToolSourceProvider、MCP 公共 API 不变。原工具构造器和 SubagentRuntime 的无作用域管理方法
保留给旧代码；新增局部控制在 native Orchestrator 链装配。旧直接 SpawnRequest 调用保留旧深度规则；不带结构化作用域的旧任务仅能通过旧管理入口访问。
控制工具属于 kernel 已有的编排控制面，与普通领域工具所在的 agent-tools 分工一致。

本批所有权是服务器装配的会话绑定，不是多租户认证：原始 sessionId 的认证归属、会话存储命名空间隔离、完整 run 身份谱系、
usage/根预算、持久化与恢复仍待实施。取消会释放本地等待并尽力停止执行，不证明远端物理副作用已撤销。

## 验收

用真实 Orchestrator、Factory、SubagentRuntime、ToolCallingLoop，替换远端 LLM/设备响应，捕获
子任务收到的工具定义、参数、dispatcher、设备上下文和结果。覆盖父拒绝/子允许、父允许/子拒绝、
嵌套权限与深度、跨会话及兄弟任务访问、伪造根 session 冒充子任务、风险上限、叶子执行、
取消后的迟到调用/完成、跨已完成中间节点的级联与名额回收。真实 Spring 驱动生产装配，
确认控制工具不进入全局目录且完成 default → worker → wait 往返。最后执行全 reactor clean test。

## 验收记录（2026-09-06）

修改前 13:58:29 全量绿色基线为 2242 项。先复现跨任务访问、上下文/权限丢失、
叶子与深度错误、取消后的迟到调用、Spring 全局控制装配、伪造 session、断层级联、
风险误标及共享内存并发缺陷，再修复并验证。内存修复后，含 30 次嵌套场景的定向验证共 108 项通过。

14:43:02 最终全 reactor `mvn clean test` 成功：2270 项，2250 通过、20 跳过，
0 失败、0 错误；13 模块全部成功，比基线新增 28 项。真实 Python 往返探针显式启用且通过。
可复现命令和续接边界见本文顶部实施入口；结果对应未提交工作区增量，尚未部署。
