# agent-kernel

The core framework module. Everything else depends on it; it depends on nothing internal.

## Responsibility
Defines all core abstractions: AgentLoop, ToolCallingLoop, Gateway, Tool, LLMProvider, PromptEngine, StreamEvent.

## Key Packages

| Package | Purpose |
|---|---|
| `.agent` | Tool (含 riskLevel), ToolRegistry, ToolScanner, AgentLoop, AgentProfile, AgentRegistry, ScopedToolRegistry, ToolPolicy, RiskLevel |
| `.agent.annotation` | `@ToolFunction`, `@ToolParam`, `@ClientTool` annotation scanning |
| `.agent.directive` | Directive system — DirectiveManager, DirectiveRegistry, DirectiveToolBridge |
| `.cli` | CliTool, CliManifest, CliToolSourceProvider, CliExecutor (LocalProcessExecutor) |
| `.core` | ToolCallingLoop, ToolExecutor, StreamEvent, ToolResultChunk, CancellationToken |
| `.core.postprocess` | StreamPostProcessor pipeline |
| `.learn` | SkillGeneratorObserver, SkillExtractor, HeuristicSkillExtractor, Trajectory |
| `.orchestrator` | Orchestrator, AgentRouter, AgentFactory, InterruptibleRun, SubagentRuntime, TriggerSource |
| `.gateway` | Gateway, ChatHandler, GatewayRequest/Response, SessionManager |
| `.llm` | LLMProvider, LLMProviderSpi (ServiceLoader), ConversationMessage, ToolCall |
| `.llm.claude` | ClaudeProvider, ClaudeProProvider, ClaudeProviderSpi |
| `.llm.openrouter` | OpenRouterProvider |
| `.llm.websocket` | WebSocket-based LLM provider |
| `.memory` | MemoryProvider, ConversationMemory, Message, MessageSnapshot |
| `.prompt` | PromptEngine, PromptSection, PromptContext, Skill |
| `.skill` | Skill loading and management |
| `.instruction` | InstructionPackage, InstructionRegistry, ProviderAdapter |
| `.speech` | Speech/TTS integration |
| `.trace` | Tracing and observability |
| `.plugin` | **Legacy** — Plugin, PluginFunction. Do NOT add new code here. |

## Rules
- Usage propagation, shared execution budgets and structurally valid compaction follow
  [ADR-026](../decisions/026-execution-usage-budget-compaction.md); missing usage is unknown,
  and estimates must remain distinguishable from service-reported consumption.
- Server-bound identity, encoded session keys and fail-closed memory scope views follow
  [ADR-025](../decisions/025-authenticated-session-memory.md). Legacy unowned calls retain
  their namespace; authenticated requests must never fall back to shared memory.
- Execution assembly follows [ADR-023](../decisions/023-execution-assembly-snapshot.md):
  freeze prompt content, resolved device tools, their definitions and LLM options at execution;
  share source snapshots with descendants, retain live authorization checks, and expose the
  actual assembly through an additive AgentObserver callback.
- Execution model binding follows [ADR-022](../decisions/022-execution-model-binding.md):
  the optional ExecutionScopedLLMProvider SPI resolves once per ExecutionModelScope;
  native descendants inherit the scope through ToolExecutionContext. Fresh root executions
  (including reactive resumes) bind again at execution time. Every ToolCallingLoop entry
  retains its selected provider across iterations. This does not freeze live cancellation or policy.
- Controlled harness evidence scenarios follow
  [ADR-021](../decisions/021-frozen-candidate-evidence-bundle.md): the real native stack
  exports captured downstream payloads only when the developer evidence directory is supplied.
  Candidate snapshotting and Maven execution belong to repository tooling, not kernel runtime APIs.
- Foreground-to-subagent cancellation follows
  [ADR-020](../decisions/020-foreground-subagent-cancellation.md): each execution's
  token travels in ToolExecutionContext; task cancellation listeners are released
  on every terminal path. Resumes use a fresh token; normal completion is not cancellation.
- Native subagent inheritance and local wait/list/stop ownership follow
  [ADR-019](../decisions/019-subagent-inheritance-and-ownership.md): ancestor denials
  survive child profile selection, device dispatch context follows spawn, and task
  access matches the runtime-owned root session plus child ID path, never parsed from
  client session text. Legacy administrative APIs remain separate.
  InMemoryProvider serializes reads/writes across concurrent parent/child runs; this
  protects its mutable lists, not user authorization or persistence.
- Request-bound tool execution follows [ADR-018](../decisions/018-request-tool-execution-scope.md):
  propagate ToolExecutionContext per invocation, register session tools locally before
  building the loop, and check actual arguments before dispatch or server execution.
- `ContextAwareTool` is an optional extension of the unchanged `Tool` SPI. ClientTool
  and its aliases use the request dispatcher while keeping payload/result conversion.
  ToolCallingLoop forwards events and collects results in one subscription so cancellation
  reaches pending client futures; it must not cache and retain their upstream subscription.
- No dependency on agent-web, agent-tools, agent-mcp, or any soul-* module.
- New LLM providers go under `.llm.<provider>`.
- New abstractions must be interfaces first, implementations second.
- InterruptibleRun owns execution cancellation and outer-stream termination;
  AgentLoop alone commits each user input. Late callbacks and cleanup are guarded
  by execution identity. Cancellation now records a terminal cause and keeps
  provider/output subscription ownership in the execution container; see
  [ADR-015](../decisions/015-voice-turn-cancellation.md) and
  [ADR-028](../decisions/028-run-lifecycle-and-cancellation.md).
- Orchestrator allocates and executes runs on subscription. Cancellation during
  Gateway/Orchestrator prefix events must not leave a RUNNING execution behind;
  the next LLM request contains only inputs that actually reached AgentLoop.

ADR-026 当前增量：原生默认 MicroCompactor(2000) 在 sync/async/reactive 每次模型调用前执行；
保留工具调用与结果的对应 ID。SnipCompactor 旧 API 保留，但不再作为原生默认链的一环。
OpenRouter 的尾部 usage 帧和 Claude 累计 usage 传回最终响应；缺失/不完整字段仍为未知。
HTTP 200 内的错误事件必须传给调用者。后续共享预算按实际尝试预留和结算，不能只统计重试最终成功。
