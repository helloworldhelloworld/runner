# ADR-025：服务器身份与统一会话记忆

- 日期：2026-09-07
- 状态：Accepted（native 隔离与存储已验收；OpenClaw 有主体入口明确拒绝）
- 前置：ADR-018、019、023

## 决策

1. GatewayRequest 增加服务器设置的主体，不能从客户端 metadata/userId 获得授权。
   REST 在认证拦截后捕获主体，再进入异步任务；两种 WebSocket 均从有效 token 解析主体。
   未认证的公开连接拒绝；旧 SDK 的无主体调用保留在未归属空间，不能访问新用户数据。
   Vert.x 在 HTTP 升级前拒绝无效 token；其现有连接/dispatcher 索引依赖 textHandlerID，
   因此显式启用 WebSocket write handlers，不依赖 Vert.x 版本变化的默认值。
   真实握手还发现 Spring Boot BOM 把 Netty 解析为 4.1.101，导致 Vert.x 4.5.12 所需类缺失。
   显式导入 Vert.x 4.5.12 自身 BOM 指定的 Netty 4.1.117.Final，使所有 Netty 模块使用一致版本。
2. 会话键逐段编码主体、agent 和客户端 session；子任务继续由运行时生成自己的 ID。
   运行槽位、interrupt、observe、历史与清空均使用同一主体映射。客户端提供的内部键只被
   当作普通 session 字符串编码，不能直接索引另一个用户或子任务。
3. MemoryProvider 增加作用域视图，未支持隔离的实现对有主体请求明确失败，不能退回全局。
   原生运行按主体/agent 选择记忆，再绑定本轮 session；历史、摘要和检索使用同一视图。
   子任务继承主体；write_memory 使用实际执行上下文中的记忆，不能写入全局工具单例的存储。
4. 生产隔离存储持久保存消息、临时记忆的来源 session 和长期记忆。清空会话删除该会话历史
   与派生摘要，避免检索再次召回；用户明确保存的长期信息通过独立记忆管理操作处理。
   原生 SessionManager 与 AgentLoop 读取同一存储，重启无需从另一路转录补写。
   初始实现采用各旧 agent 根目录下独立 SQLite，逐条参数化查询限定主体/agent；旧文件仍不导入。
   派生摘要记录来源 session；清空通过事务推进 generation，使已经清空的旧执行不能迟到补写。
   检索暂使用作用域内词项匹配，不把 Mock Embedding 的结果称作语义召回。
   清空也取消同一主体、根 agent 和 session 下仍活跃的独立子任务，即使前台已经结束；
   派生记忆清理覆盖这些任务使用的其他 agent 分区。停止与子任务注册共用运行表锁，
   被停止的任务不能继续派生新任务。其他主体、根会话和明确保存的长期记忆保留。
5. 旧共享文件与无主体历史保留为未归属数据，不在登录或启动时自动迁移给新用户。
   文件存储、认证装配属于 web；主体、会话键和作用域契约留在 kernel。
6. 外部 OpenClaw 的 agent workspace/长期记忆隔离尚未验证，不能仅编码 session 就承诺用户隔离。
   在其远端契约通过验收前，对有主体的公开请求明确拒绝；旧无主体直接 SDK 模式保留。
   原生会话存储不能代替外部 backend 的真实上下文，也不能返回另一份全局历史冒充它。

## 验收

实际认证 → Spring/Vert.x 入站 → Gateway/Orchestrator → Provider：两用户使用相同 session，
内容、长期记忆、摘要和观察事件不串；一个用户的 interrupt 不取消另一用户的执行。
用实际模型请求验证写入、重启恢复、清空后不召回和恶意 session 键；用实际工具消费验证
服务器绑定的主体和记忆视图，客户端 userId 不改变它。失效 token 的请求不能到达 Provider。

2026-09-07 03:29:24：13 模块 clean test 成功，2333 项，2313 通过、20 跳过；Python 网关
往返探针显式启用，日志 `/tmp/runner-identity-final.log`。根会话清空还覆盖跨 agent 独立后代、
派生摘要与过期写入视图。OpenClaw 拒绝入口通过远端实际收到的输入与取消列表验证。
# 补充：旧统一 Gateway HTTP 入口

`/gateway/chat`、`/gateway/chat/stream` 和 `/gateway/session/*` 与 `/api/chat` 遵守相同身份边界。
SSE 在切线程前捕获 AuthInterceptor 写入的主体；extra 中的 userId/principalId 不能覆盖它。
历史、摘要和清空先调用 SessionManager.forScope，不能落入兼容 SDK 的无主体入口。
旧公开 Java 构造器保留；Controller 自有 executor 在容器关闭时释放。
