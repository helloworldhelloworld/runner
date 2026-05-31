# Architecture Optimization Analysis — 2026-05-31

> Sources: OpenClaw (250k+ stars, Gateway/Channel/LLM 3-layer), Claude Code leaked source (v2.1.88, 512K+ lines, 1900 files), Strands Agents 1.0, OpenAI Agents SDK, LangGraph, AutoGen 0.4, CrewAI, Semantic Kernel, Spring AI 1.1+

---

## Part 1: Cross-Framework Consensus (2026 Industry Baseline)

Five major frameworks (Claude Code, OpenClaw, Strands, OpenAI SDK, LangGraph) have converged on a shared baseline. Below is what runner already has vs. what it's missing.

### Runner Already Aligned

| Pattern | Runner Implementation | Industry Standard |
|---|---|---|
| ReAct Agent Loop | `ToolCallingLoop` (LLM -> tool detect -> execute -> re-prompt) | Universal consensus |
| Reactive Streaming | `Flux<StreamEvent>` with 18 event types | Claude Code SSE, OpenClaw WebSocket, Strands async |
| Tool Registry + MCP | `ToolRegistry` + 5 supply forms + `agent-mcp` module | All frameworks support MCP as tool source |
| Multi-Agent with Scoped Tools | `ScopedToolRegistry` (deny > allow) + `SubagentRuntime` | Claude Code subagent isolation, OpenAI handoffs |
| Session Memory Isolation | `sessionKey` namespace (`agent:<id>:main/subagent:<uuid>`) | OpenClaw per-agent workspace, Claude Code session isolation |
| Context Compaction | `ContextCompactor` (Snip + Micro, 2-layer) | All frameworks have some compaction strategy |
| Interrupt/Resume | `InterruptibleRun` + `CancellationToken` propagation | Claude Code steering queue (h2A), LangGraph checkpoints |

### Runner Gaps (Detailed Below)

| Gap | Claude Code | OpenClaw | Strands | OpenAI SDK | Spring AI | Severity |
|---|---|---|---|---|---|---|
| 5-Layer Compaction | 5-layer cascade with cache-aware microcompact | Auto-compaction with pluggable providers | SlidingWindow + Summarizing | N/A | Session API with MemGPT Recall | **P0** |
| Prompt Caching | cache_edits to preserve warm prefix | N/A | N/A | N/A | N/A | **P0** |
| Tool Sandboxing | 23 regex checks + bubblewrap/Seatbelt OS sandbox | Sandbox-first with allowlists | N/A | N/A | N/A | **P0** |
| Hook System | 27 events, 5 handler types | Typed gate hooks | Composable hooks with retry signaling | RunHooks + AgentHooks | Advisor chain | **P1** |
| Guardrail Chain | 7 permission modes | N/A | N/A | Input/Output/Tool guardrails with tripwires | Tool Approval Strategy | **P1** |
| OTel Tracing | Internal tracing | Bounded tracing | OTel native | Built-in traces/spans | Micrometer integration | **P1** |
| Memory Consolidation | autoDream 4-phase (Orient/Gather/Consolidate/Prune) | Memory with provenance labels | N/A | N/A | AutoMemoryTools | **P2** |
| Lazy Tool Loading | ToolSearchTool (load schemas on-demand) | N/A | N/A | N/A | N/A | **P2** |
| Graph/DAG Orchestration | Coordinator mode | Lobster DAG language | Graph + Swarm primitives | Handoff chains | A2A routing | **P2** |
| Recall Storage | N/A | N/A | N/A | N/A | MemGPT full-log-searchable after compaction | **P2** |

---

## Part 2: TODO Items (Prioritized)

### P0 — Critical (Blocks Production Readiness)

#### TODO-P0-01: 5-Layer Compaction Pipeline

**Current state:** 2-layer (Snip + Micro). Missing Budget Reduction, Context Collapse, and Auto-compact (LLM summarization).

**Target design** (inspired by Claude Code's cascade — cheapest first, heaviest last):

```
Layer 1: Budget Reduction (Source-Level)
  - Tool results > 50K chars -> write to file, keep 2KB preview + path in context
  - Zero LLM cost, zero information loss for large outputs

Layer 2: Snip (existing SnipCompactor, enhanced)
  - Delete TOOL messages from rounds > N ago
  - Preserve USER/ASSISTANT/SYSTEM forever
  - Enhancement: configurable keepRecentRounds (currently hardcoded to 3)

Layer 3: Microcompact (existing MicroCompactor, enhanced)
  - Truncate oversized TOOL messages
  - Enhancement: cache-aware mode — when prompt cache is warm,
    emit cache_edits instead of modifying local messages
  - Requires: Prompt Caching integration (TODO-P0-02)

Layer 4: Context Collapse
  - Reduce message blocks in stages (merge consecutive same-role messages)
  - Intermediate between cheap deletion and expensive summarization
  - Triggered at ~70% context utilization

Layer 5: Auto-compact (LLM Summarization)
  - Summarize entire conversation via LLM call
  - Most expensive but most effective
  - Triggered at ~85% context utilization or manually via API
  - Post-compact: re-read recently accessed files (up to 5, 50K token budget)
```

**Files to modify:**
- `agent-kernel/.../context/CompactionChain.java` — add 3 new layers
- `agent-kernel/.../context/BudgetReductionCompactor.java` — new
- `agent-kernel/.../context/ContextCollapseCompactor.java` — new
- `agent-kernel/.../context/AutoCompactor.java` — new (requires LLMProvider injection)
- `agent-kernel/.../context/SnipCompactor.java` — make keepRecentRounds configurable
- `agent-kernel/.../context/MicroCompactor.java` — add cache-aware mode

**Acceptance test:** Set up a conversation with 100+ tool results totaling 500K tokens. Assert compaction cascade triggers layers in order, final context fits within budget, and recently accessed files are re-read post-compact.

---

#### TODO-P0-02: Prompt Caching Integration

**Current state:** No prompt caching. Every LLM request sends full system prompt + tool definitions + history from scratch.

**Target design** (from Claude Code):
- Cache structure: system prompt -> tool definitions -> CLAUDE.md equivalent -> conversation history
- Cached reads cost 10% of normal input tokens
- Cache lifetime: ~5 min inactivity; each hit resets timer
- Cache-compaction interaction: Layer 3 Microcompact uses `cache_edits` blocks to surgically remove tool results without invalidating cached prefix
- `CostTracker` must track cache hit/miss rates

**Files to modify:**
- `agent-kernel/.../llm/LLMOptions.java` — add cache breakpoint configuration
- `agent-kernel/.../llm/claude/ClaudeProvider.java` — add `cache_control` blocks to request body
- `agent-kernel/.../context/CostTracker.java` — add cache hit/miss tracking
- `agent-kernel/.../core/ToolCallingLoop.java` — call `CostTracker.record()` (fixes existing TODO-036)

**Acceptance test:** Make 3 consecutive LLM calls with identical system prompt prefix. Assert 2nd and 3rd calls report cache hits. Assert cost tracker records lower input token cost for cached calls.

---

#### TODO-P0-03: Tool Sandboxing (BashTool Security)

**Current state:** No `BashTool.java` exists. No command validation. No OS-level sandboxing.

**Target design** (from Claude Code's 23-check pipeline + OpenClaw's sandbox-first approach):

```
Step 1: Command Validation (regex-based, 15+ checks minimum)
  - Shell metacharacter injection
  - Command substitution detection ($(cmd), `cmd`)
  - Pipe/redirect injection (|, >, >>)
  - Quote escaping attacks
  - Environment variable expansion attacks
  - Path traversal (../)
  - Denied command blacklist (rm -rf /, dd, mkfs, etc.)
  - Maximum subcommand count (50) to prevent parser bypass

Step 2: Permission Check
  - Deny > Ask > Allow (first match wins)
  - Configurable per-agent via ScopedToolRegistry

Step 3: OS-Level Sandbox
  - Linux: bubblewrap (bwrap) for filesystem + network isolation
  - Fallback: Java SecurityManager (deprecated but available) or ProcessBuilder restrictions
  - Filesystem: read/write to CWD only by default
  - Network: no outbound by default, configurable allowlist

Step 4: Execution with Timeout
  - Per-tool timeout (configurable, default 30s)
  - Process tree kill on timeout (not just PID)

Step 5: Output Sanitization
  - Scan for credential-shaped strings (API keys, passwords, tokens)
  - Truncate oversized output (> 50K chars -> file + preview)
```

**Files to create:**
- `agent-tools/.../bash/BashTool.java`
- `agent-tools/.../bash/BashSecurity.java` (validation checks)
- `agent-tools/.../bash/BashSandbox.java` (OS-level isolation)
- `agent-tools/.../bash/CommandDenyList.java`

**Acceptance test:** Execute `echo hello` -> succeeds. Execute `rm -rf /` -> denied by validator. Execute `$(curl evil.com)` -> injection detected. Execute long-running `sleep 999` -> killed by timeout.

---

#### TODO-P0-04: Execution Path Unification (Existing TODO-049)

**Current state:** Two execution paths — sync (`run()`) and reactive (`runStream()`). Sync path bypasses `ToolCallingLoop`, hooks, and cost tracking.

**Target design:** `run() = runStream().blockLast()`. Single execution path. This auto-resolves:
- TODO-035: Sync/async hooks missing
- TODO-037: runStream() bypasses ToolCallingLoop
- TODO-036: CostTracker.record() never called (partially)

**Files to modify:**
- `agent-kernel/.../agent/AgentLoop.java` — `run()` delegates to `runStream().blockLast()`
- `agent-kernel/.../core/ToolCallingLoop.java` — ensure all hooks fire in reactive path
- Remove duplicate sync execution logic

---

#### TODO-P0-05: CostTracker.record() Wiring (Existing TODO-036)

**Current state:** `CostTracker.record()` has 0 callers. Cost tracking is completely non-functional.

**Fix:** 5 lines in `ToolCallingLoop.java` — call `costTracker.record(inputTokens, outputTokens)` after each LLM response in the reactive path.

**Acceptance test:** Run an agent with a budget of 1000 tokens. Assert `isOverBudget()` returns true after sufficient calls. Assert accumulated costs match sum of individual LLM response token counts.

---

### P1 — Important (Production Quality)

#### TODO-P1-01: Hook System (3-Tier with 5 Handler Types)

**Current state:** `AgentObserver` with `onPreToolUse`/`onPostToolUse` — only in reactive path, no retry signaling, no blocking/allowing decisions.

**Target design** (synthesized from Claude Code 27 events + Strands composable hooks):

```
Event Cadences:
  Session-level:  SessionStart, SessionEnd
  Turn-level:     UserPromptSubmit, TurnComplete, TurnFailure
  Loop-level:     PreToolUse, PostToolUse, PreModelCall, PostModelCall
  Compaction:     PreCompact, PostCompact

Handler Types:
  1. Command  — Execute shell command, JSON stdin/stdout
  2. HTTP     — POST to webhook endpoint
  3. MCP Tool — Call MCP server tool
  4. Inline   — Java lambda/functional interface (default)

Hook Capabilities (from Strands):
  - PreToolUse can return: allow / deny / ask / defer
  - PostToolUse.retry field: request tool re-execution for transient errors
  - PostModelCall.retry field: request model retry (quality check)
  - attemptCount field: bound retries without external state

Hook Priority:
  - Deny rules ALWAYS override hook allow decisions
  - Multiple hooks: all PreToolUse must agree (any deny = deny)
  - PostToolUse hooks run sequentially (first retry wins)
```

**Files to create/modify:**
- `agent-kernel/.../agent/hook/HookEvent.java` — enum of events
- `agent-kernel/.../agent/hook/HookHandler.java` — interface with typed responses
- `agent-kernel/.../agent/hook/HookRegistry.java` — registration + dispatch
- `agent-kernel/.../agent/hook/HookResult.java` — allow/deny/retry/defer
- `agent-kernel/.../core/ToolCallingLoop.java` — replace `AgentObserver` calls with hook dispatch

---

#### TODO-P1-02: Guardrail Chain (Input / Output / Tool)

**Current state:** `soul-safety` handles crisis detection. No general-purpose guardrail system.

**Target design** (from OpenAI SDK tripwire pattern + Spring AI Tool Approval):

```
Three Guardrail Types:
  1. InputGuardrail  — Validates user input before agent processes it
  2. OutputGuardrail — Validates agent output before user sees it
  3. ToolGuardrail   — Wraps tool execution with before/after validation

Execution Model:
  - Guardrails run IN PARALLEL with agent execution (not blocking)
  - Tripwire mechanism: when triggered, immediately halts execution
  - GuardrailResult: { passed: boolean, reason: String, action: PASS|BLOCK|MODIFY }

Built-in Guardrails:
  - PII detection (output)
  - Credential leak detection (output)
  - Injection detection (input)
  - soul-safety integration (input)
  - Tool allowlist enforcement (tool)

Custom Guardrails:
  - Interface: Guardrail<T> with evaluate(T content, GuardrailContext ctx)
  - Registered per-agent via AgentProfile
```

**Files to create:**
- `agent-kernel/.../agent/guardrail/Guardrail.java`
- `agent-kernel/.../agent/guardrail/GuardrailChain.java`
- `agent-kernel/.../agent/guardrail/GuardrailResult.java`
- `agent-kernel/.../agent/guardrail/InputGuardrail.java`
- `agent-kernel/.../agent/guardrail/OutputGuardrail.java`
- `agent-kernel/.../agent/guardrail/ToolGuardrail.java`
- `agent-kernel/.../agent/guardrail/TripwireException.java`

---

#### TODO-P1-03: OpenTelemetry Integration

**Current state:** Custom `Tracer` + `SpanContext` in `agent-kernel/.../trace/`. Not OTel-compatible.

**Target design** (OTel GenAI Semantic Conventions 2026):

```
Span Types (per OTel GenAI conventions):
  - gen_ai.agent       — Full agent turn
  - gen_ai.llm         — Single LLM call (model, tokens, latency)
  - gen_ai.tool        — Tool execution (name, args, result, duration)
  - gen_ai.guardrail   — Guardrail evaluation
  - gen_ai.compaction   — Context compaction event

Events on Spans:
  - gen_ai.prompt      — System prompt content (optional, privacy-sensitive)
  - gen_ai.completion  — Model response content
  - gen_ai.tool_call   — Tool name + arguments
  - gen_ai.tool_result — Tool result (truncated)

Metrics:
  - gen_ai.token.usage          — Input/output/cached tokens per call
  - gen_ai.agent.duration       — End-to-end agent turn duration
  - gen_ai.tool.duration        — Per-tool execution time
  - gen_ai.compaction.ratio     — Context reduction percentage
  - gen_ai.cost.total           — Estimated cost per request chain

Export:
  - OpenTelemetry SDK (auto-configured via Spring Boot starter)
  - Compatible with Jaeger, Datadog, Grafana Tempo, AWS X-Ray
  - Overhead target: < 1ms per span
```

**Files to modify:**
- `agent-kernel/.../trace/Tracer.java` — bridge to OTel SDK
- `agent-web/pom.xml` — add `opentelemetry-spring-boot-starter` dependency
- `agent-kernel/.../core/ToolCallingLoop.java` — emit OTel spans for tool calls
- `agent-kernel/.../llm/claude/ClaudeProvider.java` — emit OTel spans for LLM calls

---

#### TODO-P1-04: Circuit Breaker for Tools (Existing TODO-005)

**Current state:** No circuit breaker. Failing tools retry indefinitely.

**Target design:**

```
CircuitBreaker per tool (3-state: CLOSED -> OPEN -> HALF_OPEN)
  - CLOSED: Normal operation, count consecutive failures
  - OPEN: After N consecutive failures (default 3), reject calls for cooldown period
  - HALF_OPEN: After cooldown, allow 1 probe call
    - Success -> CLOSED (reset counter)
    - Failure -> OPEN (restart cooldown with exponential backoff)

Configuration (per-tool via ToolSchema or global default):
  - failureThreshold: 3
  - cooldownMs: 30_000
  - maxCooldownMs: 300_000
  - backoffMultiplier: 2.0

Integration:
  - ToolExecutor checks circuit state before execution
  - Open circuit returns ToolResult.error("Tool temporarily disabled: ...")
  - Model sees error and can choose alternative approach
```

---

#### TODO-P1-05: Streaming Timeout + Retry Classification (Existing TODO-003, TODO-041)

**Current state:** No streaming timeout (requests can hang indefinitely). `isRetryable()` defaults to `true` (retries on permanent failures like HTTP 401/403).

**Target design:**

```
Streaming Timeout:
  - Per-request timeout (default 120s for streaming, 60s for non-streaming)
  - Idle timeout: if no SSE event received for 30s, abort + retry
  - Configurable via LLMOptions

Retry Classification:
  - isRetryable() default: FALSE (safe default)
  - Retryable: HTTP 429 (rate limit), 500/502/503 (server error), network timeout
  - Not retryable: HTTP 400 (bad request), 401 (auth), 403 (forbidden), 404
  - Exponential backoff with jitter: base=1s, max=32s, jitter=0-500ms
  - Max retries: 3 (configurable)
```

---

#### TODO-P1-06: Tool Execution Timeout (Existing TODO-040)

**Current state:** No tool execution timeout. Runaway tools hang indefinitely.

**Fix:** Wrap `ToolExecutor.execute()` with `Mono.timeout(Duration)` in reactive path. Default 30s, configurable per-tool via `ToolSchema.timeout`.

---

### P2 — Enhancement (Competitive Features)

#### TODO-P2-01: Memory Consolidation (autoDream-Inspired)

**Current state:** `kernel-memory` has file + SQLite persistence with BM25 + vector search. No cross-session memory consolidation.

**Target design** (from Claude Code autoDream 4-phase + CrewAI multi-tier memory):

```
4-Phase Consolidation Cycle (runs during idle):
  Phase 1: Orient  — Read current MEMORY.md index, understand existing knowledge
  Phase 2: Gather  — Scan recent session transcripts for:
    - User corrections and preference changes
    - Important decisions and recurring patterns
    - Entity relationships (people, projects, technologies)
  Phase 3: Consolidate — Merge new findings into memory:
    - Resolve contradictions (newer > older)
    - Convert vague insights into concrete facts
    - Link related entities
  Phase 4: Prune — Rebuild memory index:
    - Cap at 200 lines / 25KB
    - Remove redundant or stale entries
    - Maintain provenance labels (source session, timestamp, confidence)

Memory Tiers (from CrewAI):
  Tier 1: Session Memory    — Active conversation (existing)
  Tier 2: Episodic Memory   — Daily logs, BM25-searchable (existing FileMemoryManager)
  Tier 3: Entity Memory     — People, projects, tech stack (NEW)
  Tier 4: Strategic Memory  — Learned patterns, successful strategies (NEW)
  Tier 5: Durable Memory    — Permanent knowledge, always in system prompt (existing)
```

---

#### TODO-P2-02: Lazy Tool Loading (ToolSearchTool Pattern)

**Current state:** All tool definitions loaded into every LLM request. With 20+ tools, this consumes 14-17K tokens per request.

**Target design** (from Claude Code's ToolSearchTool):

```
Core Tools (always loaded): ~5 essential tools
  - read_file, edit_file, bash, search, ask_user

Deferred Tools (loaded on demand):
  - All other tools registered in ToolRegistry but NOT sent in LLM request
  - Special "tool_search" tool always available
  - LLM calls tool_search("I need to create a branch") -> returns matching tool schemas
  - Matched schemas injected into next LLM request

Benefits:
  - Reduce per-request token cost by 60-80% (14K -> 3-5K tokens for tool definitions)
  - Reduce prompt cache invalidation (core tools don't change)
  - Scale to 100+ registered tools without context bloat
```

---

#### TODO-P2-03: Recall Storage After Compaction (MemGPT Pattern)

**Current state:** Compacted content is discarded. No way to search old conversation history after compaction.

**Target design** (from Spring AI Session API — MemGPT Recall Storage):

```
Full verbatim event log is ALWAYS retained in persistent storage (SQLite/file).
After compaction:
  - Working context: compacted (summarized/truncated)
  - Recall storage: full original messages, keyword-searchable
  - recall_search tool: LLM can search old conversation history
  - Returns relevant excerpts from pre-compaction messages

Implementation:
  - Before compaction, persist all messages to RecallStorage
  - RecallStorage: BM25 index over full message text
  - Add recall_search tool to ToolRegistry
  - Tool returns top-K relevant messages with timestamps and context
```

---

#### TODO-P2-04: Conditional System Prompt Assembly

**Current state:** System prompt is built in `PromptEngine` / `AgentProfile.systemPrompt` as a monolithic block.

**Target design** (from Claude Code's 110+ conditional instructions):

```
System prompt assembled from sections, each with activation conditions:
  PromptSection {
    id: String
    content: String
    condition: Predicate<PromptContext>  // e.g., "only if agent has bash tool"
    priority: int                       // ordering within assembled prompt
    cacheable: boolean                  // eligible for prompt caching
  }

PromptContext provides:
  - Available tools
  - Agent profile
  - Session state (new vs. resumed)
  - User preferences
  - Active skills

Benefits:
  - Smaller prompts when features not relevant (saves tokens)
  - Sections can be individually cached
  - Easy to add/remove instructions per-agent without string manipulation
```

---

#### TODO-P2-05: Graph/DAG Orchestration Mode

**Current state:** `SubagentRuntime` supports spawn/wait/list (fire-and-forget or blocking collect). No structured graph orchestration.

**Target design** (from Strands 1.0 Graph + LangGraph StateGraph):

```
Three Orchestration Modes:
  1. Spawn/Wait (existing) — Fire-and-forget with optional collect
  2. Pipeline (new)        — Sequential chain: A -> B -> C, output feeds next input
  3. Graph (new)           — DAG with typed edges, parallel branches, join points

Graph API:
  AgentGraph.builder()
    .addNode("planner", plannerProfile)
    .addNode("coder", coderProfile)
    .addNode("reviewer", reviewerProfile)
    .addEdge("planner", "coder")           // planner output -> coder input
    .addEdge("planner", "reviewer")        // parallel branch
    .addJoinPoint("merge", "coder", "reviewer")  // wait for both
    .build();

Execution Semantics:
  - Superstep: all nodes with satisfied dependencies execute in parallel
  - Atomic failure: if any node in superstep fails, pending writes from other nodes are preserved (LangGraph pattern)
  - Checkpoint at each superstep boundary for resume capability
```

---

#### TODO-P2-06: Agent Handoff Modes (Existing TODO-007, TODO-052)

**Current state:** Spawn-only subagent mode. No delegate or handoff semantics.

**Target design** (from OpenAI SDK handoffs + Claude Code 3 models):

```
Three Handoff Modes:
  1. Spawn (existing)     — Parent continues, child runs in parallel
  2. Delegate             — Parent pauses, child runs, result returns to parent
  3. Handoff              — Parent exits, child takes over conversation
                            (user now talks directly to child agent)

Use Cases:
  - Spawn: "research this while I continue coding"
  - Delegate: "ask the security expert to review this, then continue"
  - Handoff: "transfer this conversation to the support agent"
```

---

### P3 — Future (Strategic Differentiation)

#### TODO-P3-01: A2A Protocol Integration

Cross-framework agent communication via Google's Agent-to-Agent protocol. Expose runner agents as A2A-compliant servers. Consume external A2A agents as tool sources.

#### TODO-P3-02: Steering Queue (Mid-Task Interjection)

Claude Code's dual-buffer async queue (h2A) sustaining >10k msg/s. Enables user to redirect agent mid-task without full restart. Current `InterruptibleRun` cancels and restarts; a steering queue would allow non-destructive course correction.

#### TODO-P3-03: Distributed Agent Runtime (Actor Model)

AutoGen 0.4's actor-model core enables agents across processes/machines. Each agent is a micro-actor processing messages one at a time, with message delivery decoupled from handling.

#### TODO-P3-04: Tool Argument Augmenter

Spring AI innovation: dynamically augment tool input schemas with additional arguments (reasoning, confidence, inner thoughts) before sending to LLM. Enables explainable tool decisions.

---

## Part 3: Dependency Graph & Execution Order

```
Phase 1 (Foundation — unblocks everything):
  TODO-P0-04  Execution Path Unification
  TODO-P0-05  CostTracker.record() Wiring (5 lines)
  TODO-P1-05  Streaming Timeout + Retry Classification
  TODO-P1-06  Tool Execution Timeout (15 lines)
       |
       v
Phase 2 (Core Infrastructure):
  TODO-P0-01  5-Layer Compaction Pipeline
  TODO-P0-02  Prompt Caching Integration
  TODO-P1-01  Hook System (unblocks TODO-P0-03, TODO-P1-02)
       |
       v
Phase 3 (Security + Safety):
  TODO-P0-03  Tool Sandboxing (BashTool)
  TODO-P1-02  Guardrail Chain
       |
       v
Phase 4 (Observability + Intelligence):
  TODO-P1-03  OpenTelemetry Integration
  TODO-P1-04  Circuit Breaker
  TODO-P2-01  Memory Consolidation
  TODO-P2-02  Lazy Tool Loading
       |
       v
Phase 5 (Advanced Features):
  TODO-P2-03  Recall Storage
  TODO-P2-04  Conditional System Prompt Assembly
  TODO-P2-05  Graph/DAG Orchestration
  TODO-P2-06  Agent Handoff Modes
       |
       v
Phase 6 (Strategic):
  TODO-P3-01  A2A Protocol
  TODO-P3-02  Steering Queue
  TODO-P3-03  Distributed Runtime
  TODO-P3-04  Tool Argument Augmenter
```

---

## Part 4: Quick Wins (< 1 Day Each)

| # | TODO | Effort | Impact |
|---|---|---|---|
| 1 | TODO-P0-05: CostTracker.record() | 5 lines | Fixes complete cost-tracking non-functionality |
| 2 | TODO-P1-06: Tool execution timeout | 15 lines | Prevents runaway tool hangs |
| 3 | TODO-P1-05 (partial): isRetryable() default false | 20 lines | Prevents retry-on-401/403 |
| 4 | TODO-P0-04: run() = runStream().blockLast() | ~50 lines | Eliminates 3 related bugs |
| 5 | TODO-P0-03 (partial): CLI command blacklist | 30 lines | Basic OWASP compliance |

---

## Part 5: Key Insights from External Analysis

### From Claude Code (98.4% Infrastructure Rule)

> Only 1.6% of Claude Code's codebase is AI decision logic. The remaining 98.4% is deterministic infrastructure: permission gates, context management, tool routing, recovery logic.

**Implication for runner:** The core agent loop is already solid. The highest-ROI improvements are in infrastructure: compaction, caching, sandboxing, observability. Don't over-invest in making the LLM smarter; invest in making the infrastructure around it more robust.

### From OpenClaw (Workspace-First Configuration)

> Agent identity defined by plain text files (SOUL.md, IDENTITY.md, AGENTS.md). Version-controllable with Git, editable with any text editor.

**Implication for runner:** Current `AgentProfile` is code-based. Consider supporting markdown-file-based agent definitions (like Spring AI's agent registry) for non-developer agent configuration. Lower priority but improves accessibility.

### From Strands (Hook-Based Retry)

> `AfterToolCallEvent.retry` field allows hooks to request tool re-execution. `attemptCount` bounds retries without external state.

**Implication for runner:** The hook system (TODO-P1-01) should include retry signaling from day one. This is a pattern unique to Strands that solves a real problem (transient tool failures) elegantly.

### From OpenAI SDK (Guardrail Tripwire)

> Guardrails run IN PARALLEL with agent execution. Tripwire mechanism: when triggered, immediately halts execution.

**Implication for runner:** Guardrails should not be blocking pre-checks. They should race against the agent and cancel on violation. This is architecturally different from sequential validation.

### From LangGraph (Checkpoint + Pending Writes)

> When a node fails mid-superstep, completed writes from other nodes are preserved. On resume, only failing branches re-execute.

**Implication for runner:** `InterruptibleRun` currently discards partial state on interrupt. Preserving successful subagent results across interrupts would significantly improve resume quality.

### From Spring AI (MemGPT Recall Storage)

> Full verbatim event log is always retained and keyword-searchable, even after compaction.

**Implication for runner:** Compaction currently means permanent information loss. A recall storage layer would let the model recover compacted information on demand, significantly improving long-session quality.

---

## Appendix: Source References

### OpenClaw
- [GitHub: openclaw/openclaw](https://github.com/openclaw/openclaw) (250K+ stars)
- [Architecture: Gateway/Channel/LLM Layers](https://eastondev.com/blog/en/posts/ai/20260205-openclaw-architecture-guide/)
- [Design Patterns Analysis (7-part series)](https://kenhuangus.substack.com/p/openclaw-design-patterns-part-1-of)
- [Multi-Agent Orchestration](https://learnopenclaw.org/multiagent.html)
- [Compaction](https://docs.openclaw.ai/concepts/compaction) | [Memory](https://docs.openclaw.ai/concepts/memory)
- [Claude Code vs OpenClaw: 5 Design Dimensions (ByteByteGo)](https://blog.bytebytego.com/p/ep214-claude-code-vs-openclaw-5-design)

### Claude Code
- [VILA-Lab: Dive into Claude Code (arXiv 2604.14228)](https://arxiv.org/abs/2604.14228)
- [5-Layer Compaction Cascade](https://finisky.github.io/en/claude-code-context-compaction/)
- [Prompt Caching Internals](https://www.claudecodecamp.com/p/how-prompt-caching-actually-works-in-claude-code)
- [Sandboxing (Official)](https://code.claude.com/docs/en/sandboxing)
- [Hooks Reference (Official)](https://code.claude.com/docs/en/hooks)
- [autoDream Memory Consolidation](https://claudefa.st/blog/guide/mechanics/auto-dream)
- [Source Leak Analysis (VentureBeat)](https://venturebeat.com/technology/claude-codes-source-code-appears-to-have-leaked-heres-what-we-know/)

### AI Agent Frameworks
- [Strands Agents 1.0](https://strandsagents.com/docs/user-guide/concepts/agents/agent-loop/) | [Hooks](https://strandsagents.com/docs/user-guide/concepts/agents/hooks/) | [Multi-Agent](https://strandsagents.com/docs/user-guide/concepts/multi-agent/multi-agent-patterns/)
- [OpenAI Agents SDK](https://openai.github.io/openai-agents-python/) | [Guardrails](https://openai.github.io/openai-agents-python/guardrails/)
- [LangGraph State Management 2026](https://eastondev.com/blog/en/posts/ai/20260424-langgraph-agent-architecture/)
- [CrewAI Memory Systems](https://www.crewship.dev/learn/crewai-memory)
- [Spring AI Session API](https://spring.io/blog/2026/04/15/spring-ai-session-management/) | [Tool Argument Augmenter](https://spring.io/blog/2025/12/23/spring-ai-tool-argument-augmenter-tzolov/)
- [OTel GenAI Semantic Conventions](https://opentelemetry.io/blog/2026/genai-observability/)
- [Multi-Agent Production Patterns 2026](https://niteagent.com/blog/multi-agent-production-2026/)
