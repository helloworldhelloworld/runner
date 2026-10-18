# Runner 当前实施状态与剩余工作

更新：2026-10-18。本文件是编码 Agent 的当前工作入口，承接[主方案 §12.4](runner-minion-system-evolution.md#124-2026-09-06-实施进展与续接点)。下方“历史进展日志”只保留证据，不代表当前待办。

| 顺序 | 交付 | 状态 | 完成证据 |
|---|---|---|---|
| 1 | 执行装配：Provider、提示、工具实现/定义、参数及可观察清单 | 核心链已验收（ADR-023）；模型预设持久化已实现（ADR-031）；Profile 管理扩展仍需收口 | 同轮固定、子任务继承、新轮更新；下游真实请求/工具回包与清单一致；密钥不落盘 |
| 2 | 发布：候选/评测内容绑定、原子激活、回滚、重启恢复及实际消费 | 已验收（ADR-024） | 修改草稿/用例后旧报告拒绝；失败保留旧版本；下一轮实际使用通过的版本 |
| 3 | 服务器身份、会话命名空间、统一持久记忆与历史/清空 | native 与旧 `/gateway` 主入口已验收（ADR-025）；OpenClaw 对有主体请求保持明确拒绝 | 两用户同 session 不串运行/历史/摘要/长期记忆；重启、伪造键、清空与独立后代验收 |
| 4 | usage、共享根预算、循环中压缩与停止 | runner 主链与 Web 协议已验收（ADR-026）；真实付费模型账单和 Pi 语音现场仍属外部验证 | 实际/未知用量分开记账，父子共享预算，并发预留受限，结构化停止到达客户端 |
| 5 | 持久任务、检查点、恢复和结果投递 | 文件存储、服务、有界 runner、主体隔离 API、持久投递队列与失败重试已实现（ADR-029） | 重启后未知运行进入 `RECOVERY_REQUIRED`；仍缺真实外部系统副作用核对、生产触发器和部署侧 consumer |
| 6 | 通用受控证据、反馈索引、候选比较与分析交接 | 候选工作流、比较 CLI 和持久反馈索引/API 已实现（ADR-021/027/030） | 同一候选及评测可比较；仍缺通用线上 trace 摄取、自动分析和按最终源码刷新系统候选包 |
| 7 | Pi 唯一设备所有者、真实能力、播放回执、Realizer | 能力错误回传已验收；所有权与播放回执仍待实施 | 四仓自动化接缝；无设备明确失败；迟到隔离与部分播放事实正确 |
| 8 | 真设备、声学/机械指标、部署组合及持续运行 | 等待验收环境信息；先准备可执行验收 | 实际 Pi/相机/舵机/眼睛与播放测量，自动化结果不能替代实测 |

执行方式：逐项先写 ADR 和跨组件失败验收，再实现并回归；保留旧公共 API 和已有工作区修改。
认证迁移不把旧共享数据默认归给任何新用户；保存为未归属数据并提供显式迁移入口。
具体部署和实机条件单独记录，不把暂不可验收的硬件项目标为完成。

## 当前验证基线（2026-10-18）

- 提交基线：`2c3b9f1`（Minion 运行时可靠性、持久化与改进闭环）。
- `mvn clean test`：13 个模块成功；2380 项测试，2358 通过、22 跳过、0 失败、0 错误。
- 默认环境没有启用可选的跨仓 Python voice-gateway 探针；跳过项不能替代 Pi、云 STT/TTS、付费模型或真实硬件验收。
- 当前软件主线剩余项集中在真实外部副作用核对、生产任务触发/投递、线上 trace 分析，以及 Pi 播放与设备事实。

## 下一批建议

1. E/F：收敛 Pi 唯一设备实例、播放游标、Realizer 和播放回执，先完成受控跨仓验收，再做实机校准。
2. G：为持久任务接入一个真实生产触发器、可查询的外部副作用账本和部署侧投递 consumer。
3. J：把线上 trace 摄取绑定主体、运行装配和反馈索引，并用当前源码重新生成可比较候选包。
4. H：在固定四仓版本与硬件清单后，执行相机、舵机、眼睛、AEC、声学、电源、温升和持续运行验收。

每次 Dot/Codex 任务只选择一个工作包，完成时记录提交、自动化结果、跳过的外部检查和仍未跨越的边界。

## Dot 接手约定

Dot 负责持续跟踪目标、依赖和待决策事项，具体代码修改交给从仓库根目录启动的 Codex 任务。
首次接入时连接本仓库及可复现构建环境，并要求每个任务先读 `AGENTS.md`。一次只推进上述一个
工作包；没有实机、外部服务或部署证据时，不得把模拟/受控对端结果升级为现场完成。每次交付至少
返回分支或提交、执行过的测试、跳过项、文档更新和下一条未跨越边界，合并与推送仍由明确授权决定。

## 历史进展日志

新基线：2026-09-07 00:10:14，13 模块 clean test 成功（2303 项，2283 通过、20 跳过），Python 网关往返探针显式启用。

ADR-023 进展：模型实际工具版本、设备 schema、共享提示缺失的 6 项失败验收已复现并修复；
新增同源子任务提示继承、装配清单与深层参数快照验收。00:26:11 的 13 模块 clean 回归通过；
正在补充旧 ToolPolicy 对工具对象身份的兼容边界，然后进入 ADR-024 的持久发布。

2026-09-07 00:55:46：ADR-023/024 的 13 模块 `mvn clean test` 全绿，2324 项，2304 通过、20 跳过；日志 `/tmp/runner-publication-final.log`。
发布新增真实 SQLite/HTTP 验收 7 项；报告缺失/重复结果、取消与完成写入失败终态另外 3 项。
当前进入 ADR-025 的认证与统一存储；后续 4—8 尚未完成，不能把本次发布验收当作整个架构完成。

2026-09-07 02:18 续接：ADR-025 的真实 REST/Spring WebSocket/Vert.x WebSocket、SQLite 重启与
清空派生摘要定向验收已经通过。新增跨 agent 独立子任务清空验收复现清空后仍 RUNNING，
正在以 `clearExecutionTree` 与根任务所有权停止收口；全量日志 `/tmp/runner-identity-tree-full.log`
仍在运行，不能先标为通过。前一次 full 的直接控制器单测残留请求上下文已修复。

设备侧同时核对生产入口：`real_eyes_device()` 的缺失相机/舵机仍返回模拟成功，零串口也可能
返回成功。minion-body 基线 120 项（115 通过、5 跳过），新增真实 SDK 往返失败验收准备中。
SDK v1 FastMCP 依赖已限定 `<2`，因为当前无上界安装会解析到存在破坏性变更的 v2。
设备文档在配套仓 `docs/device-availability.md`；尚未运行真硬件。

2026-09-07 02:26：设备可用性增量已通过真实 MCP SDK 1.29.1 往返：生产缺失硬件返回错误，
串口提交不冒充显示确认，断链返回错误。minion-body 122 项（120 通过、2 项缺 NumPy 跳过），
日志 `/tmp/minion-production-green.log`。其余第 7 项保持未完成。脚本基线发现独立 Maven
版本探针冷启动超时，已改成从同一次实际执行的 `--show-version` 采集；正在定向复验后接候选比较。

2026-09-07 03:29:24：ADR-025 全量通过，13 模块、2333 项（2313 通过、20 跳过），日志
`/tmp/runner-identity-final.log`。本机负载较高，构建使用 Maven 与测试 JVM 的 384 MB 堆上限、
2 个可用处理器，保留全部测试并显式启用 Python 网关探针。候选比较与原有脚本共 19 项通过；
仍需重新运行最终系统候选样本。下一步 ADR-026 的实际 HTTP/SSE 用量和循环内压缩。

2026-09-07 03:57：Provider/循环压缩内核 1291 项全过；全量在 MCP 首次 JDK WebSocket 冷启动
触发测试自设 5 秒请求期限（日志实际建连 14.8 秒）后中断。成功往返验收采用生产默认 30 秒
请求期限、增加真实 JSON-RPC payload 断言；原连接失败/断连快失败测试保留，定向 13 项通过
（`/tmp/runner-mcp-deadline-green.log`），重新 clean 全量中。不得把失败 full 记为成功。
minion-body 在隔离测试环境补装 NumPy 后 122 项全部通过、无跳过（`/tmp/minion-production-complete.log`）。
预算的父子共享、实际输出限制、未知消费及重试验收已写入隔离工作目录 `/tmp/runner-budget-work`，
RED 日志 `/tmp/runner-budget-red.log`，正在实现；尚未合回工作区或标记完成。

2026-09-07 04:10:53：隔离目录的 ADR-026 定向 44 项通过（`/tmp/runner-budget-green.log`）。
包含实际 Profile 输出参数/预算停止、缺失 usage 不退款、async 压缩、父子共享、并发取消、
逐次重试用量，以及 Claude 缓存输入与凭据日志修复。实现仍在 `/tmp/runner-budget-work`；
完整内核回归 `/tmp/runner-budget-kernel-full.log` 进行中，尚未合回或完成 Spring 装配/客户端验收。
客户端验收草案：`/tmp/ExecutionBudgetWebAcceptanceTest.java`、`/tmp/BudgetWebSocketAcceptanceTest.java`。

04:18 续接发现：`AgentLoop.runStream` 旧回调仍直接调 Provider，尚未受共享预算约束；
Gateway.handleStream / Orchestrator.chatStream 的最终响应也会丢掉停止原因。新增失败验收草案
`/tmp/BudgetCallbackAcceptanceTest.java`，下一步在完整内核回归后先运行 RED，再统一回调链。
语音侧同样需要 budget_stopped 的 turnId/schema/路由/异常，否则会误当成空回复完成；ADR-026
已补充决策。此时 root 的 `/tmp/runner-provider-usage-clean.log` 在 agent-web，隔离预算内核
`/tmp/runner-budget-kernel-full.log` 尚在编译测试；两者均不能预记为通过。

2026-09-07 04:53:02：主工作区 pre-budget 的 13 模块 clean 全绿，2342 项（2322 通过、20 跳过），
日志 `/tmp/runner-provider-usage-clean.log`。包括实际 Provider usage/SSE 错误和每轮 Micro 压缩，
不包括尚在隔离目录的共享预算实现。期间系统时钟跳过约半小时，构建总耗时不能当作性能指标。
隔离预算完整内核 1304 项出现 5 项失败：4 项旧取消验收期待取消后继续请求模型，1 项用 100ms
阈值近似异步返回。取消验收已改等真实 Agent 终态再检查设备，spawn 验收改为远端结果未完成时
检查返回的 runId 与真实任务输入，然后释放受控结果并验收返回值；`/tmp/runner-budget-cancellation-green.log`
复验中。旧历史 HarnessEvidence 的取消字段随新事实更新，需要重建候选，不复用旧报告。

2026-09-07 05:25 续接：共享预算的 18 个 Java 文件已合回主工作区；取消/Harness 的 7 项
和实际异步 spawn 的 5 项定向通过。回调问题已冻结真实修复前候选并运行：25 项、4 项失败，
实际 HTTP 请求和 stopReason 均写入证据。修复后候选已冻结，评测尚在执行：
- baseline：`/tmp/runner-budget-callback-before-20260907`，候选摘要
  `638e023aa9d9d72458ff0782730a1fce46fc9efff5f4b184a713bf12ed6ab589`；
- candidate：`/tmp/runner-budget-callback-after-20260907`，候选摘要
  `404e8a2f39563641c8dc603dac27eacea04a5ec298bd3fafa2281c000b794719`；
- 共同评测摘要：`00043748b7e0b21f07ebe94c514e068ed34884ebc6a365e419d6f16c9a34f1d3`。
这组比较只覆盖 agent-kernel 的 25 项行为，不等于整个系统通过。两份快照不得手改。
回调修改已接统一 reactive 工具循环、AgentResponse usage、Gateway/Orchestrator 停止 metadata，
保留旧公共构造器/入口。Web 三项验收已加入，`/tmp/runner-budget-web-red.log` 构建中；
当前 Web serializer 新枚举尚未接通，不能称全量可编译。
voice-gateway 的真实 WebSocket→TextBridge→播放预算验收已先失败后修复，包含停止后终止读取、
迟到隔离、缺失 turnId 拒绝和无取消参数时 abort。全量 149 项首次仅共享 schema 定义集合
未更新而失败（8 项缺可选依赖跳过），正在补充 schema 验证并重跑，不能预记全绿。

2026-09-07 06:09:35：冻结回调候选首次运行修复了 4 项目标缺陷，但另一个旧测试在第一个
模型请求到达前超过自设 5 秒期限（未发生工具调用），因此首次比较如实记录 regressed：
`/tmp/runner-budget-callback-comparison-first-20260907`。保留原失败报告后，使用 replay 原样
复跑同一候选和评测，25 项全部通过：`/tmp/runner-budget-callback-after-replay-20260907`。
复跑期间本机时钟跳跃约半小时，总耗时不是性能数据。新的比较输出为
`/tmp/runner-budget-callback-comparison-verified-20260907`，仍须核验报告生成结果。
Web RED 已先复现 77 变 4096 与 Spring 缺 turnId 两项断链，已补对应装配/载荷；
新 Python 真实消费者探针、旧两套 callback SSE 和 WebSocket Provider 边界加入下一轮 RED。
发现 `/gateway` 旧 HTTP 入口未传登录主体，ADR-025 补充范围并新增真实 HTTP/模型请求隔离验收。
voice-gateway 完整 151 项全部通过，包含真实 DashScope 1.27.4 SDK 与 WebSocket 17.1 本地对端、
共享 schema 验证；日志 `/tmp/voice-budget-sdk-full.log`。可选依赖仅安装于隔离 venv
`/tmp/runner-body-completion-env`；本地测试设置 NO_PROXY/no_proxy 为 localhost 与 127.0.0.1。
SDK 导入有冷启动，日志有 unclosed event loop ResourceWarning；通过不等于无资源告警。

2026-09-08 00:23：Web 预算边界继续按失败先行收口。首次 Web 验收复现 Profile 输出上限
未传递（模型请求收到 4096 而不是 77）、Spring 关联预算帧缺 turnId、普通 SSE 把停止
原因放在错误层级，以及旧 `/gateway` 入口没有使用服务器主体。已补 OrchestratorConfig
的输出/预算配置、两类 WebSocket/SSE 的 `budget_stopped` 结构化事件、Spring 关联 turnId、
旧 Gateway 的 `AuthenticatedRequests` 与 `SessionManager.forScope`。WebSocket 两条预算验收
通过，普通 SSE 预算验收通过；旧 Gateway 身份验收和跨仓 Python seam 的下一次结果仍需随
全量 clean 回归确认。OpenAI 格式 WebSocket Provider 的真实对端验收仍显示：usage/流内
error 处理存在问题，已加入修复（严格 usage、错误终止、去除完整请求日志），需回归确认。

截至本段，预算回调比较报告为 `improved`（25 项全通过的 replay，固定评测摘要不变），
voice-gateway 151 项全部通过（3 项厂商 SDK 测试在无云环境跳过；本地 venv 已安装 SDK
后仍只使用受控本地对端）。持久任务/检查点、在线反馈索引、Pi 唯一设备所有者与播放游标、
真实硬件声学/机械验收仍未完成；这些不因本轮 Web 或语音模拟对端而标记完成。

2026-09-08 00:36：新增边界回归已完成，`WebSocketUsageAcceptanceTest` 5 项、
`BudgetWebSocketAcceptanceTest` 2 项、`LegacyGatewayIdentityAcceptanceTest` 4 项、
`ExecutionBudgetWebAcceptanceTest` 2 项全部通过；跨仓 `BudgetVoiceSeamAcceptanceTest`
在未提供可运行 Python 网关时按环境条件跳过。此前全仓 clean 暴露的 `MinionVoiceTurnWebSocketTest`
仍有 2 个取消断言失败和 2 个子代理取消超时，集中在 Spring 语音连接切换/断开时订阅建立的竞态；
已加入逻辑 session 级 turn 替换和显式 attach 取消，但尚未把该回归标记为解决，下一步继续以
真实远端 sink 的取消时序收口后再重跑全仓 clean。OpenAI WebSocket usage、流内 error、预算 Web/SSE
和旧 Gateway 主体隔离边界目前均有可重复的绿色定向证据；这不等于全仓 clean 已绿。

2026-09-08 00:55：为连接切换增加了显式 logical subscription 索引，并在 turn attach 时处理取消竞态；定向复验仍未通过，旧远端 sink 的 `isCancelled` 观察值依旧未及时变化。该实现暂不作为绿色证据，后续需从测试所用远端 sink 与 Reactor 取消传播的边界继续定位。

2026-09-08 01:00：取消生命周期改动已通过 `mvn -q -pl agent-web -am -DskipTests package` 编译；Minion 取消验收仍保持失败，尚未纳入绿色回归结果。

2026-09-09：在新增订阅生命周期代码后复跑边界回归仍通过：OpenAI WebSocket 5 项、WebSocket 预算 2 项、旧 Gateway 身份 4 项、SSE 预算 2 项，构建成功。Spring `MinionVoiceTurnWebSocketTest` 的单场景旧 sink 取消断言仍失败；该问题独立于上述边界，继续保留为未完成项。

2026-09-09：连接接管时补充对旧 `VoiceChatTurn.subscription` 的直接 dispose，并完成 agent-web 全模块编译；预算、OpenAI WebSocket、SSE、身份隔离边界继续保持通过。Spring 语音测试中的远端 `FluxSink.isCancelled()` 仍未变绿，剩余问题已收敛到 Reactor sink 的取消与完成信号语义，需要继续在 `InterruptibleRun` 的 active sink/subscription 生命周期上处理。

2026-09-10：按第一性原理新增 ADR-028，明确逻辑 session、单一 active run、执行终态、取消原因以及 provider/output subscription 的唯一拥有者。`InterruptibleRun` 已开始记录终止原因并区分用户可接续打断与不可接续取消，保持旧 Gateway/Tool/MCP API 兼容；kernel 定向 InterruptibleRun/VoiceTurnCancellation 测试 11 项通过。WebSocket provider sink 的取消传播仍需按新生命周期模型继续迁移，尚未宣称全仓完成。

2026-09-10 00:31：新增 `ActiveRunCoordinator` 作为 Orchestrator 的单一 session→active run 索引；shutdown 会以 `SYSTEM_SHUTDOWN` 取消残留执行，TTL 清理不再直接操作传输层 map。kernel 的 InterruptibleRun/VoiceTurnCancellation 定向 11 项仍全通过。

2026-09-10 00:45：新增 `ChatHandler.cancel`、`Gateway.cancel` 生命周期入口，Orchestrator 按逻辑 session 通过 `ActiveRunCoordinator` 处理 `CONNECTION_REPLACED` / `CLIENT_DISCONNECTED`，Spring handler 不再直接调用大脑 interrupt 处理连接接管和断开。agent-web 编译通过；语音测试的 provider sink 取消观察仍失败，说明还需把 provider 实际 subscription 的注册点继续下沉到 AgentLoop/ToolCallingLoop。

2026-09-10 00:52：执行上下文新增可选 resource registrar；ToolCallingLoop 在 provider 订阅建立时登记实际 cancel handle，InterruptibleRun 取消时释放 provider resources 与输出订阅。kernel 11 项生命周期回归通过；Spring 最小接续场景仍未通过，继续保留真实 sink 信号验证。

2026-09-10 00:59：定位并修复最终竞态：A 轮取消在释放资源前没有在锁内捕获自己的 `activeResources`，B 轮接续会重置该字段，导致取消错释放到 B。现在取消路径捕获并释放原执行自己的 provider resource composite。`MinionVoiceTurnWebSocketTest` 9 项 0 失败 1 跳过；预算/OpenAI/WebSocket/SSE/身份边界 18 项 0 失败 2 跳过；默认 Surefire fork 模式下全仓 `mvn clean test` 13 模块 BUILD SUCCESS，agent-web 433 项 0 失败 20 跳过，总耗时约 3 分 22 秒。`forkCount=0` 的一次失败是测试运行器未加载 SQLite JDBC，不作为代码回归证据。

2026-09-10 01:15：开始推进持久任务主线，新增 ADR-029、`DurableTask`/`TaskStatus`/`TaskCheckpoint`、`TaskStore` 和原子替换的 `FileTaskStore`。已验证幂等创建、检查点写入、进程重启读取和旧版本 compare-and-set 拒绝（1 项定向测试通过）。这交付了持久任务存储基础；调度器、真实外部副作用核对、结果投递和 Web API 仍需继续接入。

2026-09-10 01:13：持久任务继续推进：`TaskStore.list`、`DurableTaskService` 已接入，支持 start/checkpoint/succeed/fail；启动恢复会把未知 RUNNING 尝试保守转为 `RECOVERY_REQUIRED`，拒绝盲目重跑。任务服务定向测试 2 项通过。Web 调度入口、外部副作用幂等核对和结果投递仍待接入。

2026-09-10 01:18：持久任务服务已接入 agent-web：`DurableTaskService` 由 OrchestratorConfig 装配，新增 `/api/tasks` 创建/查询/启动/checkpoint/完成入口，所有读取和变更按当前主体隔离。默认文件路径可由 `app.tasks.store-file` 配置；Web API 的调度执行器与外部副作用核对仍保持独立，避免接口假装已经执行任务。

2026-09-12：持久任务继续接入后台执行层：新增 `DurableTaskRunner`，支持有界 worker、任务尝试落盘、成功/失败终态、显式取消和重复提交去重；`RECOVERY_REQUIRED` 任务拒绝自动重放。任务存储/服务/runner 定向测试 3 项通过。结果投递、外部副作用核对和真实调度触发仍待接入。

2026-09-12：持久任务继续补齐结果可靠性：新增持久化 `TaskDelivery`/`FileTaskDeliveryStore`，按 taskId+channel 幂等入队，版本化确认并支持重启后读取待投递项；新增 `EffectLedger` 作为当前进程内副作用幂等保护，后续需替换为按外部系统确认结果持久化的实现。任务存储/runner/delivery 4 项测试通过。

2026-09-12：继续推进在线改进闭环，新增 ADR-030 与 `FeedbackRecord`/`FeedbackIndex`/`FileFeedbackIndex`。反馈记录追加保存，按 feedbackId 幂等，绑定 principal、task/run、candidate/evaluation 摘要，可按候选和反馈类型检索；不会直接修改线上 Skill/Prompt。定向测试 1 项通过。

2026-09-12：反馈索引已接入 agent-web：新增 `/api/feedback` 写入和按 candidateDigest/kind 查询接口，主体由服务器会话注入，客户端不能指定归属主体；Spring 默认持久路径可由 `app.feedback.store-file` 配置。agent-web 编译通过。

2026-09-12：模型配置闭环继续收口：新增 ADR-031 与 `ModelPresetStore`，预设采用原子替换文件持久化并在启动时恢复；API key 仅保留在进程内，落盘内容只含 provider/model/baseUrl，恢复预设沿用当前运行时密钥。保留两参数 controller 构造器兼容独立调用。新增持久化安全测试与模型装配回归，均通过。

2026-09-12：结果投递新增 `TaskDeliveryDispatcher`，按当前待投递快照尝试发送，仅在 consumer 成功后版本化确认；发送失败的项保持 pending，可由下一轮重试。该机制已用失败后重试验收覆盖，外部通道仍由部署侧注入 consumer。
