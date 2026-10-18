# 认证与会话隔离续接检查（2026-09-06）

本次读取工作区代码后确认：已有登录服务可作为后续身份来源；仅增加 userId 字段不能完成隔离。
认证、运行槽位、历史键、摘要检索与工具访问需要一并设计迁移。本记录不把下列问题标记为已修复。

| 边界 | 已核对现状 | 下一批验收 |
|---|---|---|
| Spring WS 入站 | AuthSessionService 能解析 token；handler 仅在 token 非空时验证，userId 放入连接属性；WsChatRequests 未将身份传入 GatewayRequest | 无凭证/失效凭证拒绝；身份来自服务器；payload 的 userId 不可冒充 |
| 原生运行槽位 | Orchestrator.activeRuns 按原始 sessionId 索引，interrupt 也使用原始值 | 两个已认证用户选相同 sessionId，不能互相打断/接续 |
| 消息历史 | 根键拼接 agentId 和 sessionId，子键继续拼接子任务路径 | 分段编码根会话与子任务命名空间；客户端输入不能生成已有内部键 |
| 摘要与长期记忆 | AgentFactory 注入 sharedMemory；AgentLoop 调用无用户作用域的 search/readDurable/writeEphemeral；FileMemoryAdapter 共用 FileMemoryManager | 用户 A 的历史、摘要、长期记忆及记忆工具结果均不能进入 B 的 Provider 请求 |
| 传输一致性 | Spring 与 Vert.x 共用 WsChatRequests，但认证装配不同；观察流也按 sessionId 分组 | REST/SSE、两种 WS、观察、历史/清空使用同一个服务器身份与会话键映射 |

先定义服务器主体和会话键映射、旧未归属数据的迁移规则，再接 MemoryProvider 的作用域与生产文件存储。
旧共享记忆不能自动归给任意新登录用户；不要只修复聊天历史而继续从全局检索召回他人摘要。
设备类型/版本匹配与已认证设备 ID 是两个字段；设备注册、授权与具体执行器归属需单独验证。

本轮先完成 [ADR-020](../decisions/020-foreground-subagent-cancellation.md) 的前台取消联动，
认证与统一存储仍是实施文档 A/D 的下一批工作，不以当前 token 取消链代替身份授权。
