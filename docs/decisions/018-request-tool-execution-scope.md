# ADR-018：请求上下文与工具执行作用域

- 日期：2026-09-06
- 状态：Accepted（工作区实现和自动化验收完成，未部署）
- 对应实施文档：[系统演进方案](../design/runner-minion-system-evolution.md) §12.2 A/B

## 问题与证据

`GatewayRequest.deviceContext` 没有通过 `Orchestrator → AgentLoop` 到达
`ToolCallingLoop.executionContext`。reactive 工具执行也没有使用客户端 dispatcher。
`ScopedToolRegistry` 仅在列举阶段以 `args=null` 过滤，实际参数没有被策略检查。
`spawn_subagent` 在 AgentLoop 构建后调用 scoped registry 的 `register`，该方法会写入全局表，
既错过本轮工具定义快照，也会把当前会话的父 session 绑定泄漏给后来的运行。

## 决策

1. 沿现有 `ToolExecutionContext` 传递不可变的请求执行上下文，包含设备类型与当前连接的
   dispatcher；通过新增重载接入同步、reactive 和打断接续。每次接续使用新请求上下文。
   `GatewayRequest` 增加 typed builder/getter；传输层只注入服务器持有的 dispatcher。
2. 增加 `ScopedToolRegistry.registerLocal`，在构建 AgentLoop、生成工具定义之前注册。
   原 `register` 委托父表的兼容语义保持。局部同名工具覆盖父表只发生在当前 scope。
3. 工具执行入口在设备实现解析后、产生副作用之前，使用实际参数再次校验策略和启用状态。
   拒绝结果返回工具循环，不允许回落到同名 legacy function 绕过拒绝。
4. 保留 `Tool` SPI。通过新增 `ContextAwareTool` 接入需要上下文的工具，包括现有
   `ClientTool` 模板及别名；保留 payload 转换与回包校验。reactive 客户端派发支持取消和超时，
   释放 dispatcher 的 pending 状态。
5. `ToolCallingLoop` 的工具结果转发与下一轮输入收集使用同一次可取消订阅。
   原 `cache()` 即使下游取消仍保持上游工具执行，会使客户端 dispatcher 长期占用。
   改为随流收集最终结果，再通过 defer 启动下一轮，避免重放缓存切断取消传播。

## 验收与边界

先以跨组件测试证明设备分发、参数拒绝和工具定义/父会话归属，再覆盖双连接客户端完整往返。
完整 reactor 构建必须保持绿色，包括既有语音取消验收和公共 API 调用者。

本决策先修复现有请求到工具的执行边界。设备类型不是认证身份，客户端 sessionId 也不是
租户授权依据。本批不宣称完成 tenant/user/device 身份认证、子 Agent 权限与预算继承、
wait/list 的所有权收敛、持久化恢复、Pi 播放事实或自动改进发布闭环。


## 完成记录

2026-09-06 13:39 完成 13 模块 `mvn clean test`：2242 项，2222 通过、20 跳过，
0 失败、0 错误；显式启用真实 Python WS 探针。新增 15 项测试覆盖本批修复。
跨组件验收包括两个 Spring WS 的交错工具往返、取消在途工具后立即复用、隐式接续使用新连接，
以及 A/B 父运行交错 spawn 时实际子任务与 session key 的归属。风险过滤与同名 legacy fallback
之间的拒绝绕过也已复现并修复。实现范围及后续任务见实施文档 §12.4；未完成的边界仍按上节保留。
