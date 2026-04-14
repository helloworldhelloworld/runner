# agent-kernel

The core framework module. Everything else depends on it; it depends on nothing internal.

## Responsibility
Defines all core abstractions: AgentLoop, ToolCallingLoop, Gateway, Tool, LLMProvider, PromptEngine, StreamEvent.

## Key Packages

| Package | Purpose |
|---|---|
| `.agent` | Tool, ToolRegistry, ToolScanner, AgentLoop, AgentProfile, AgentRegistry, ScopedToolRegistry |
| `.agent.annotation` | `@ToolFunction`, `@ToolParam`, `@ClientTool` annotation scanning |
| `.agent.directive` | Directive system — DirectiveManager, DirectiveRegistry, DirectiveToolBridge |
| `.core` | ToolCallingLoop, ToolExecutor, StreamEvent, ToolResultChunk, CancellationToken |
| `.core.postprocess` | StreamPostProcessor pipeline |
| `.orchestrator` | Orchestrator, AgentRouter, AgentFactory, InterruptibleRun, SubagentRuntime |
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
- No dependency on agent-web, agent-tools, agent-mcp, or any soul-* module.
- New LLM providers go under `.llm.<provider>`.
- New abstractions must be interfaces first, implementations second.
