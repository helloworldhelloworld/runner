# Conventions

## Package Structure

Each module follows this pattern: `com.lightweightai.<domain>`.

| Module | Root Package | Sub-packages |
|---|---|---|
| agent-kernel | `com.lightweightai.kernel` | `.agent`, `.core`, `.orchestrator`, `.gateway`, `.llm`, `.memory`, `.prompt`, `.skill`, `.plugin`, `.instruction`, `.speech`, `.trace` |
| agent-tools | `com.lightweightai.tools` | `.client`, `.math`, `.time`, `.weather`, `.web` |
| agent-mcp | `com.lightweightai.mcp` | — |
| kernel-memory | `com.lightweightai.kernel.memory` | `.chunk`, `.embedding`, `.file`, `.index`, `.model`, `.queue`, `.tools` |
| agent-sdk | `com.lightweightai.agent` | `.exception`, `.memory`, `.plugin` |
| agent-web | `com.lightweightai.web` | `.agent`, `.config`, `.controller`, `.gateway`, `.model`, `.observer`, `.plugin`, `.postprocess`, `.service`, `.skillcreator`, `.websocket` |
| soul-safety | `com.lightweightai.safety` | — |
| soul-assessment | `com.lightweightai.assessment` | `.model`, `.scale` |
| soul-user | `com.lightweightai.user` | `.model` |

## Naming Conventions

- **Interfaces**: Noun or adjective (e.g. `Tool`, `LLMProvider`, `ChatHandler`)
- **Implementations**: `Default` prefix or descriptive name (e.g. `DefaultDirectiveManager`, `ClaudeProvider`)
- **Wrappers/Adapters**: `XxxWrapper`, `XxxAdapter` (e.g. `McpToolWrapper`, `ClaudeProviderAdapter`)
- **Annotations**: `@ToolFunction`, `@ToolParam`, `@ClientTool`
- **Events**: `StreamEvent`, `ToolResultChunk` — immutable value objects
- **Builders**: `Xxx.builder()` pattern (e.g. `Gateway.builder()`)

## Code Style Rules

1. **Reactive by default** — New streaming code uses `Flux<StreamEvent>`, not callbacks.
2. **Interface-first** — Define the interface in agent-kernel, implement in the appropriate module.
3. **Constructor injection** — No field injection. All dependencies via constructor.
4. **Immutable value objects** — Events, results, configs should be immutable.
5. **No legacy patterns in new code** — Don't use `Plugin`/`PluginFunction`; use `Tool` interface. Plugin exists only as legacy fallback.

## Module Boundary Rules

- New tools go in `agent-tools`, NOT in `agent-kernel`.
- New LLM providers go in `agent-kernel` under `com.lightweightai.kernel.llm.<provider>`.
- Web/REST/WebSocket code goes in `agent-web`, NEVER in `agent-kernel`.
- MCP-specific code goes in `agent-mcp`.
