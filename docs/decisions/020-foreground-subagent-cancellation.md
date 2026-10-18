# ADR-020：前台执行取消与子任务联动

- 日期：2026-09-06
- 状态：Accepted（工作区实现与自动化验收完成）
- 前置：[ADR-015](015-voice-turn-cancellation.md)、[ADR-018](018-request-tool-execution-scope.md)、[ADR-019](019-subagent-inheritance-and-ownership.md)

## 问题

前台 InterruptibleRun 取消了自己的订阅与 CancellationToken，但 native spawn 没有携带该信号。
因此断开连接、显式打断或新输入替换旧轮后，旧子任务仍可能执行设备操作。
按 sessionId 停止全部子任务会误伤同一会话的新轮，也无法表达接续执行的独立生命周期。

## 决策

1. 每次前台执行使用已有的独立 CancellationToken；接续生成新 token。
   AgentLoop 将该 token 放入本次 ToolExecutionContext 的不可变副本，保留 dispatcher、设备与超时。
   native spawn 将同一 token 传给所有层级的子任务，模型参数不能替换它。
2. CancellationToken 增加可解除的取消回调注册：取消只生效一次，注册晚于取消时立即通知，
   已解除的注册不再调用；回调在内部锁之外运行，一个回调失败不能阻止其他任务清理。
3. SubagentRuntime 注册任务后、构建/提交执行前绑定取消回调。取消与注册交错时，任务要么
   在注册前被拒绝，要么直接进入 CANCELLED；提交前或 worker 入口已观察到取消时不再启动模型执行。
   绑定晚于终态时立即解除；完成、失败、停止与构建/提交失败均解除回调。
4. 前台显式打断、隐式替换、下游取消（含断开连接）和执行失败，取消该执行关联的活跃子任务。
   使用 token 对象和运行时父子关系识别所属执行，不按会话字符串批量停止。
   子任务取消后的迟到模型工具调用仍由执行入口拒绝。
5. 前台正常完成不触发取消，保留现有异步任务语义；后代可在父任务正常完成后继续运行。
   子任务单独 stop 不反向取消前台或兄弟任务。已经生成结束后的语音停播继续由 ADR-015 处理，
   不是本批追溯取消已结束前台的入口。持久 Task 与显式脱离前台的产品语义仍待设计。
6. 保持现有 Tool/ToolSourceProvider/MCP 公共契约与流事件形状；使用已有 SUBAGENT_CANCELLED、
   AGENT_INTERRUPT、SPEECH_INTERRUPTED，取消不编码成 TRACE 字符串。

## 验收

真实 Gateway → Orchestrator → AgentLoop → spawn → SubagentRuntime；仅替换远端 LLM/设备。
覆盖旧轮已有多代任务、显式打断、取消订阅、失败、同会话接续、新轮设备上下文、旧清理晚到、
任务注册期间取消与正常完成。断言子任务终态、设备实际收到的参数及下一次模型请求。
另让真实 Spring/Tomcat WebSocket 驱动 chat → spawn → interrupt / 断连，验证传输取消确实到达子任务。
注册回调的竞态另作聚焦测试，不通过 sleep 或重试掩盖问题；最后运行全 reactor clean test。

2026-09-06 验证记录：20:18:59 完成修改前绿色基线（2270 项，2250 通过、20 跳过）；
首批跨组件用例先出现 4 项预期断言失败，修复后通过。最终 20:45:46 完成全量
`mvn clean test`：2285 项，2265 通过、20 跳过，0 失败/错误，13 模块全部成功；新增 15 项。
真实 Spring/Tomcat WS 的 interrupt/断连及显式启用的 Python 往返探针均通过。
完整命令、用例入口与续接边界见[实施进展](../design/runner-minion-system-evolution.md#124-2026-09-06-实施进展与续接点)。
本地日志 `/tmp/runner-foreground-final.log` 仅为本次过程记录，复现以工作区源码和验收命令为准。

## 边界与后续

这里只传播前台执行生命周期，不是认证身份或持久运行谱系；认证与会话存储迁移的已核对缺口见
[续接检查](../reviews/2026-09-06-auth-session-followup.md)。根预算仍需先接通真实 usage。
本地取消尽力中断远端等待；不响应中断的同步 Provider 仍可能迟到返回，后续工具调用由本地入口拒绝。
已发生的物理副作用不能由取消 token 撤销。
