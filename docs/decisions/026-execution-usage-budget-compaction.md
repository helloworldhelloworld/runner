# ADR-026：执行用量、共享预算与循环中压缩

- 日期：2026-09-07
- 状态：Proposed
- 前置：ADR-022、023、025

## 问题

CostTracker 目前只在 reactive 循环开始时被检查，没有从实际模型响应累计用量，也没有接入
原生 AgentFactory 或子任务继承。流式 Provider 丢失 usage；压缩只在第一轮模型调用前发生，
工具结果增长后不会再次压缩。单纯删除 TOOL 消息还可能留下没有对应结果的 tool call。

## 决策

1. 保留旧 CostTracker API；实际 usage 与未知用量分别记录。空缺不能计为已确认的零。
   Provider 将 HTTP/SSE 的用量传回 LLMResponse，不能用完成次数或文本长度冒充实际 usage。
2. 根执行创建共享预算作用域，后代继承同一记账与准入状态；新根执行重新创建。
   所有调用入口都检查余额，记录已完成调用，预算停止有结构化终态与可观察用量。
   并发调用须先预留，完成后结算；没有 usage 的调用保留保守记账并标记不确定性。
3. 区分服务返回的累计 token、调用前估算/预留和金额。缺少模型 tokenizer 或服务 usage 时，
   不声称已经提供精确账单上限。重试与取消后未知消耗的边界明确记录，不能凭空退还额度。
4. 每次模型调用前压缩本轮上下文，工具定义与最新输入保留。压缩工具输出时保留 tool call ID
   及对应结果结构；测试以实际下一次 Provider 请求能被消费为终点。
5. Profile 与 Spring 装配显式传递输出上限和根预算；旧默认保持无限预算兼容行为。
   已有 Tool/LLMOptions/StreamEvent 公共契约只增加成员，停止事件使用明确枚举和工厂。

## 验收

真实 Provider HTTP/SSE 用量回传；sync/async/reactive 相同记账；父子共用余额、并发不得重复
预留同一余额；缺失 usage、失败与取消留有明确记录；循环中大工具输出被压缩且结果 ID 仍匹配。
配置入口设置的预算必须影响实际调用次数/请求参数和结构化停止原因。

## Provider 协议依据

- [OpenRouter streaming](https://openrouter.ai/docs/api_reference/streaming)：usage 可能在内容结束后
  单独到达，不能把 finish_reason 当作停止读取的依据；流内错误即使 HTTP 为 200 也必须失败。
- [Claude streaming](https://platform.claude.com/docs/en/build-with-claude/streaming)：message_start
  与 message_delta 共同组成最终用量；message_delta 的 token 是累计值，不能逐帧相加。
  error 事件需要传递失败，未知扩展事件可以忽略。自动化应使用真实 HTTP/SSE 客户端，控制远端帧。
- [DSH session](https://github.com/deepseek-ai/deepseek-harness/blob/d347e703908d0406b7a7ef80e3a0e594d86b2215/docs/subsystems/session.md)：
  缺少 usage 与确认的零消耗是不同事实，失败/取消尝试也要保留记录。

实现次序：先贯通 Provider 用量与错误语义及循环中消息压缩，再加入父子共享准入、预留、
结算和结构化停止。隐藏重试、取消后的远端消费及未配置 tokenizer 的估算须标明来源和
不确定性，不能用一次最终成功响应代表所有失败尝试的真实费用。

## 预算实现约束

- `ExecutionUsageScope` 持有根预算、进行中预留与逐次尝试账目，通过 `ToolExecutionContext`
  不可变副本传给后代；子 Profile 不能重置根余额。直接调用 ToolCallingLoop 也创建作用域。
- 模型边界的 `UsageTrackingLLMProvider` 对 sync/future/reactive 使用同一预留与结算实现。
  `ResilientLLMProvider` 将此包装应用于实际 delegate，因此每次重试各自准入，且共享原有健康统计。
  预算拒绝不可重试；已经发出流内容后不可重试整个请求。自定义 Provider 内不可见的重试仍须由
  实现者公开尝试边界；框架不能推断远端隐藏执行。
- 无 tokenizer 时按完整消息/元数据/工具定义的 UTF-8 字节与固定封装余量估算输入。此值只用于
  保守准入，账目明确命名 `estimatedInputTokens` / `reservedTokens`；真实 token 来源只有 usage。
  余额大于输入估算才允许输出，必要时缩小本次 `maxTokens`，该值必须体现在实际 HTTP 请求。
- 进行中、失败、取消或成功但缺失 usage 的尝试不释放未确认消耗；已知完整 usage 才以真实值结算。
  服务用量超出估算时如实记录超支并阻止后续调用。取消不能证明远端停止计费。
- `AgentProfile.maxTokens` 默认 4096，`maxBudgetTokens` 默认 0（无限）；Spring 原生装配从
  `app.orchestrator.max-output-tokens` / `app.orchestrator.max-budget-tokens` 传入所有默认 Profile。
- 准入失败产生 `BUDGET_STOPPED`，携带原因、预算、已知 token、未知次数及预留余额快照。
  sync/future 返回 `stopReason=budget_exhausted`；reactive 发出一次结构化事件并结束，不能伪造模型
  成功响应或把额度提示写成模型记忆。REST SSE 与两种 WebSocket 必须向最终客户端转发此事件。
- 装配清单记录配置上限，尝试账目记录实际缩减后的输出上限。两者语义不同，比较工具需保留区别。

Claude 的总输入还包括 `cache_creation_input_tokens` 与 `cache_read_input_tokens`，不能只用
`input_tokens`；见 [官方缓存用量定义](https://platform.claude.com/docs/en/build-with-claude/prompt-caching#tracking-cache-performance)。
预算按 token 数累计，不按缓存价格折扣换算金额。新增字段为空或非法时保留未知，避免低估。
请求日志不得包含 API key 或携带凭据的完整 headers/body；实际请求仍保持兼容网关认证方式。

相同约束覆盖自定义 OpenAI 格式 WebSocket Provider：完整帧或流帧中的 usage 只能在输入/
输出两项都合法时确认为已知；流内 error 传递给消费者，不能记录为空白成功。兼容既有
finish_reason 即结束的网关，若其把 usage 放在该终态之后，提前关闭可能无法取得尾部用量，
此时保留未知预留，不以零退还；不能声称所有自定义网关具有 HTTP SSE 的完整尾帧保证。
实际 WS 请求仍传递 max_tokens 与原认证字段，日志只记录结构规模，不输出完整请求正文。

语音消费者同样必须收到停止原因：Spring 的 correlated 轮次为 `budget_stopped` 补回 `turnId`，
共享文本 schema 增加同名出站定义；voice-gateway 的轮次路由将它视为目标轮的终态，TextBridge
抛出保留 usage 的预算停止异常，触发现有 TTS/播放清理。缺失 turnId 的停止不能按到达时序
归到新轮。Vert.x 既有普通 chat 路径保留停止数据；其完整 correlated voice 支持单独验收。

回调兼容入口也须走同一工具循环：`AgentLoop.runStream` 不得直接绕过 ToolCallingLoop。
同步/回调的 AgentResponse 与 GatewayResponse 保留 `stopReason` 和用量快照，回调协议通过
新增默认预算回调与最终 metadata 保留原因；不能把预算停止合成为成功的空模型完成事件。
现有 public 构造器和方法保持，新增 usage 数据不包含原始提示、记忆或凭据。

取消作用域贯通后，旧子任务的迟到回包不能触发下一次模型调用。验收等待真实 Agent 完成
回调中的 `stopReason=cancelled`，随后检查设备未收到动作；不再要求模型额外消费一次 denied
工具结果。这是新的停止行为，不以测试等待或额外模型调用换取旧验收通过。
