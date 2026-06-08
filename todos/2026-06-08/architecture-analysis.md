# Architecture Analysis & Optimization TODOs — 2026-06-08

## Research Sources

- **OpenClaw** (github.com/openclaw/openclaw): 200K+ stars, "One Process Five Subsystems" architecture, built on PI (TypeScript toolkit), markdown-based skill system (SKILL.md) adopted as open Agent Skills standard
- **Claude Code** (leaked v2.1.88 source, ~1900 files / 512K+ lines TypeScript): 98.4% deterministic infrastructure + 1.6% AI decision logic; analyzed via VILA-Lab "Dive-into-Claude-Code" project
- **Community frameworks**: OpenAI Agents SDK (handoff-based), LangGraph v1.0 (graph-based), CrewAI (role-based), Google ADK (hierarchical)

---

## Current Runner Architecture Summary

- 12 Maven modules, Java 21, Reactor 3.6 streaming
- 4-layer Orchestrator: AgentProfile → AgentRegistry → Orchestrator → SubagentRuntime
- 2-layer ContextCompactor: SnipCompactor + MicroCompactor
- 5 ToolSourceProviders: LegacyScan, Manual, DynamicPlugin, MCP, CLI
- 17 StreamEvent types, Flux<StreamEvent> unified pipeline
- ScopedToolRegistry (deny > allow) per agent
- 3-layer Memory: Session, Ephemeral/Durable, BM25+Vector Search

---

## GAP ANALYSIS: Runner vs Claude Code vs OpenClaw vs Best Practices

### 1. Context Compaction Pipeline — GAP: SIGNIFICANT

**Runner (current):** 2-layer chain (SnipCompactor + MicroCompactor)
**Claude Code:** 5-layer "cheapest first, heaviest last" pipeline:
1. Tool Result Budget — prune oldest tool results when count exceeds threshold
2. History Snip — GC stale conversation scaffolding
3. Microcompact — clean up old tool results, leveraging `cache_edits` for server-side cache management
4. Context Collapse — projection-based folding (~90% compression, non-destructive)
5. Auto-Compact — session-maintained structured markdown notes used as summary (no LLM call needed)

**Gap:** Runner is missing layers 1, 4, and 5. The Tool Result Budget (cheapest layer) and Auto-Compact (smartest layer) are the highest-value additions. Tool observations consume 70-80% of token budget in typical sessions.

### 2. Permission & Safety System — GAP: SIGNIFICANT

**Runner (current):** ScopedToolRegistry with allow/deny lists, basic tool filtering
**Claude Code:** Deny-first, 4-principle permission system:
- Default deny with human escalation
- Graduated trust spectrum
- Defense in depth with layered mechanisms (Bash alone has 25+ validators: regex, shell-quote parsing, tree-sitter AST)
- Reversibility-weighted risk assessment
- Every tool has `isReadOnly` property

**Gap:** Runner has coarse-grained tool-level permissions but lacks command-level analysis within tools (especially Bash/CLI tools), trust graduation, and risk-weighted assessment. No `isReadOnly` classification for tools.

### 3. Subagent Execution Models — GAP: MODERATE

**Runner (current):** Single spawn model via SubagentRuntime (async spawn + wait + cascade stop)
**Claude Code:** Three distinct execution models:
- **Fork** — independent copy, fully isolated
- **Teammate** — collaborative peer, shared context awareness
- **Worktree** — git-worktree-based filesystem isolation

**OpenClaw:** Primary/Subagent with per-agent MEMORY.md, isolated agentDir workspace

**Gap:** Runner supports only async spawn. No git-worktree isolation for parallel file-editing agents, no collaborative peer model, no workspace isolation beyond session key namespacing.

### 4. Skill/Prompt System — GAP: MODERATE

**Runner (current):** Java `Skill` interface with triggers, priority, tools, registered via PromptEngine
**OpenClaw:** Markdown-based `SKILL.md` files — became the open Agent Skills standard adopted by Claude Code, GitHub Copilot, and OpenAI Codex
**Claude Code:** CLAUDE.md + skill markdown injection into system prompt on-demand

**Gap:** Runner's skill system is code-only (Java interface). No markdown-based skill definitions, making community contribution harder. No hot-reloadable skill definitions without recompilation.

### 5. Real-time Steering & Mid-task Correction — GAP: MODERATE

**Runner (current):** InterruptibleRun with interrupt/resume (full-duplex)
**Claude Code:** "h2A" asynchronous dual-buffer queue enabling mid-task course correction without restart

**Gap:** Runner's interrupt/resume is binary (stop + restart). No buffer queue for injecting corrections into an in-progress tool execution chain without losing partial results.

### 6. Cache Management & Optimization — GAP: MODERATE

**Runner (current):** No explicit prompt cache management
**Claude Code:** Tracks 14 cache-break vectors; leverages Anthropic API `cache_edits` to delete from server-side cache without invalidating cached prefix; cache-aware compaction ordering

**Gap:** Runner doesn't track or optimize for LLM provider prompt caching. Cache-aware message ordering and compaction could significantly reduce API costs.

### 7. Observability & Tracing — GAP: MODERATE

**Runner (current):** TRACE events, AgentObserver hooks, CostTracker
**Best practices (2026):** OpenTelemetry integration, distributed tracing across agent chains, token budget dashboards, tool execution timing, agent decision audit trails, cost attribution per agent/tool

**Gap:** Runner has building blocks (AgentObserver, CostTracker, TRACE events) but lacks structured observability integration. No OpenTelemetry spans, no per-tool timing metrics, no cost attribution breakdown.

### 8. State Reconstruction & Persistence — GAP: MODERATE

**Runner (current):** MemoryProvider with session/ephemeral/durable layers
**Claude Code:** Full state reconstructible from message history — enables persistence, replay, and compression without external state store
**OpenClaw:** LMDB + SQLite high-performance storage, per-agent memory directories

**Gap:** Runner's state is split across MemoryProvider, InterruptibleRun, and SubagentRuntime. No unified state snapshot/restore for crash recovery or session migration.

### 9. Error Recovery & Resilience — GAP: LOW-MODERATE

**Runner (current):** ResilientLLMProvider with retry/backoff, CancellationToken for graceful exit
**Best practices:** Checkpoint/resume for long-running tasks, state hashing for loop detection, monotonic progress checks, explicit termination conditions

**Gap:** No checkpoint/resume for multi-step agent tasks. No loop detection (agent repeating same tool calls). No progress monotonicity checks.

### 10. MCP Integration Maturity — GAP: LOW

**Runner (current):** McpToolSourceProvider, McpToolWrapper, JSON-RPC protocol, MCP server config
**Best practices (2026):** Capability negotiation during initialization, connection pooling, health monitoring, auto-reconnection, scoped permissions per MCP server

**Gap:** Runner has functional MCP integration. Could improve with health monitoring, auto-reconnection on transport failure, and per-server permission scoping.

---

## PRIORITIZED TODO LIST

### P0 — High Impact, Addresses Significant Gaps

- [ ] **TODO-001: 5-Layer Context Compaction Pipeline**
  - Add Layer 0: ToolResultBudget — prune oldest N tool results when count exceeds threshold (cheapest compaction, biggest win)
  - Add Layer 3: ContextCollapse — projection-based message folding (~90% compression, non-destructive, requires LLM summarization call)
  - Add Layer 4: AutoCompact — maintain structured session notes (markdown) throughout conversation; use as summary without LLM call when compaction needed
  - Refactor CompactionChain to support configurable layer ordering with "cheapest first" strategy
  - Track compaction metrics: tokens saved per layer, compaction trigger frequency

- [ ] **TODO-002: Tool Permission Enhancement**
  - Add `isReadOnly()` method to Tool interface (default false)
  - Implement command-level analysis for CliTool: parse commands for destructive operations (rm, git push --force, etc.)
  - Add permission tiers: AUTO_ALLOW (read-only), PROMPT_USER (write), DENY (dangerous)
  - Implement graduated trust: tools earn higher trust after N successful safe executions per session
  - Add reversibility score to ToolSchema for risk-weighted execution decisions

- [ ] **TODO-003: Prompt Cache Optimization**
  - Track cache-break vectors: system prompt changes, tool list changes, message insertions/deletions
  - Implement cache-aware compaction: when compacting, prefer operations that preserve the cached prefix
  - Add cache hit/miss metrics to CostTracker
  - Support Anthropic API `cache_control` / `cache_edits` in ClaudeProvider

### P1 — Moderate Impact, Addresses Moderate Gaps

- [ ] **TODO-004: Subagent Execution Models**
  - Implement WorktreeSubagent: git-worktree-based isolation for parallel file editing
  - Implement TeammateSubagent: collaborative model where agents share a read-only view of each other's progress
  - Add SubagentIsolationStrategy interface: SHARED (current), WORKTREE, FORK
  - Extend SpawnSubagentTool to accept isolation strategy parameter

- [ ] **TODO-005: Markdown-based Skill System**
  - Support `SKILL.md` format alongside Java Skill interface
  - Implement MarkdownSkillLoader: parse markdown skill files at runtime
  - Hot-reload skill definitions from `skills/` directory without restart
  - Define skill manifest format compatible with OpenClaw/Claude Code conventions
  - Keep Java Skill interface for complex skills needing code execution

- [ ] **TODO-006: Mid-task Steering (Dual-buffer Queue)**
  - Implement message injection queue in InterruptibleRun: user messages queued during tool execution
  - After current tool completes, inject queued messages before next LLM call
  - Preserve partial results — no restart needed
  - Add STEERING_INJECTED event type to StreamEvent

- [ ] **TODO-007: Structured Observability**
  - Add OpenTelemetry span creation in AgentLoop, ToolCallingLoop, ToolExecutor
  - Per-tool execution timing: start/end timestamps, duration in ToolResult
  - Cost attribution: tokens consumed per agent, per tool, per request chain
  - Export metrics via Micrometer (Spring Boot native) for Prometheus/Grafana
  - Add traceId/spanId to StreamEvent for distributed tracing

- [ ] **TODO-008: State Snapshot & Recovery**
  - Implement AgentState snapshot: serialize current message history + tool state + memory refs
  - Store snapshots at configurable intervals (every N tool calls or M tokens consumed)
  - Implement AgentState.restore(): reconstruct AgentLoop from snapshot
  - Enable session migration: serialize state → transfer → deserialize on new instance

### P2 — Lower Impact, Polish & Future-proofing

- [ ] **TODO-009: Agent Loop Detection & Progress Monitoring**
  - Track tool call history hash: detect repeated identical tool calls (same name + same args)
  - Add monotonic progress check: if no new information in last N iterations, break with explanation
  - Configurable max-repeat threshold per tool (e.g., allow 3 retries of bash but 1 retry of search)
  - Emit AGENT_LOOP_DETECTED event when loop is broken

- [ ] **TODO-010: MCP Health & Reconnection**
  - Add health check heartbeat to MCP server connections
  - Auto-reconnect on transport failure with exponential backoff
  - Emit MCP_RECONNECT event when connection is re-established
  - Connection pool for multiple MCP server instances
  - Per-MCP-server permission scoping in ScopedToolRegistry

- [ ] **TODO-011: Enhanced Memory Architecture**
  - Per-agent memory directories (OpenClaw pattern): each agent gets isolated `memory/` dir
  - Structured session notes: auto-maintain markdown summary of key decisions/findings during conversation
  - Memory compaction: summarize old session memory segments to reduce retrieval noise
  - Cross-agent memory bridge: allow read-only access to other agents' memory via explicit permission

- [ ] **TODO-012: Provider-Native API Features**
  - Support Anthropic compaction API (`compact-2026-01-12`) in ClaudeProvider
  - Support extended thinking / reasoning token streaming as separate event type
  - Add REASONING_DELTA event type to StreamEvent for thinking/reasoning streams
  - Support multi-modal tool results (images, files) in ToolResult

---

## Supplementary Findings (Second Research Pass)

### Additional Claude Code Details

- **The core loop**: A single `while(true)` spanning 1,421 lines (lines 307-1728 in `query.ts`). The `QueryEngine` is 46K lines handling the entire LLM interaction lifecycle. A community Rust rewrite proved the core can be expressed in ~1,530 lines across 18 files — all complexity is in surrounding systems.
- **Tool inventory**: ~19 built-in tools (up to 60+ with extensions). Notable tools not in Runner: `TaskCreate/Get/Update/List/Stop` (parallel processing engine), `SleepTool` (proactive mode), `AskUserQuestionTool`, `LSPTool` (Language Server Protocol), `CronCreateTool` (scheduled triggers).
- **SSE event types**: 9 types — `message_start`, `content_block_start`, `content_block_delta` (with `text_delta`/`input_json_delta`/`thinking_delta`), `content_block_stop`, `message_delta`, `message_stop`.
- **Sub-agent cost optimization**: Main session on Opus for complex reasoning, sub-agents on Sonnet for focused tasks. Coordination through shared task list (markdown file) with file locking.
- **Permission system detail**: 7 permission modes plus ML-based classifier for risky operations. Unicode sanitization against prompt injection. 4-level config scope: Managed (enterprise) > User > Project > Session.

### Additional OpenClaw Details

- **Config-first identity** via multiple markdown files: `SOUL.md` (personality/values), `TOOLS.md` (capabilities), `IDENTITY.md` (personalization), `HEARTBEAT.md` (autonomous schedule). "No Python, no chains, no graphs — just config files."
- **Heartbeat autonomous loop**: Runs as persistent background daemon (systemd/LaunchAgent) with configurable heartbeat (default 30 min). Fundamentally different from request-response — it's a continuously running process.
- **ClawHub skill marketplace**: 13,700+ community-published skills. Skills are `SKILL.md` with YAML frontmatter + natural-language instructions.
- **Memory**: SQLite FTS5 full-text index over local Markdown files. Memory Vault accumulates interaction history across sessions.

### New Industry Patterns (Not in Initial Analysis)

1. **A2A Protocol (Agent-to-Agent)**: Released by Google April 2025, contributed to Linux Foundation June 2025. Uses HTTP + SSE + JSON-RPC 2.0. Agent Cards for capability advertisement. 150+ organizations support it. Sits alongside MCP (agent-to-tool) to form the interoperability stack.

2. **Output Validation Between Agents**: Every agent output validated against typed schema (Pydantic/Zod) before passing to next agent. Missing this causes cascading failures in multi-agent chains.

3. **Eval Gates**: Pair runtime tracing with automated scorers that grade agent outputs, can block regressions or flag quality drops. "Step-level tracing is the minimum viable signal for production agents."

4. **LangGraph State Management Warning**: >60% of production incidents tied to state management. Migration from in-memory to persistent state store is critical for production.

5. **Context Drift**: 65% of enterprise AI failures in 2025 attributed to context drift or memory loss during multi-step reasoning — not raw context exhaustion. This reframes the compaction problem.

6. **Prompt Cache Economics**: Reduces per-call cost 50-90%. Optimization target shifts from "minimize context size" to "maximize cache hit rate."

7. **Failure Rates**: 5-15% agent failure rate is normal in production — plan for it with circuit breakers, fallback agents, and degraded-mode operation.

### Additional TODOs from Supplementary Findings

- [ ] **TODO-013: A2A Protocol Support**
  - Implement Agent Cards for capability advertisement
  - Support HTTP + SSE + JSON-RPC 2.0 transport for inter-agent communication
  - Enable external agent systems to discover and invoke Runner agents
  - Priority: P2 (future-proofing, emerging standard)

- [ ] **TODO-014: Config-first Agent Identity**
  - Support markdown-based agent definition files (similar to OpenClaw SOUL.md/TOOLS.md)
  - AgentProfile loadable from YAML/markdown config without code changes
  - Hot-reload agent personality/tools/permissions from config directory
  - Keep Java AgentProfile for programmatic use; markdown as declarative alternative
  - Priority: P1 (improves extensibility and community adoption)

- [ ] **TODO-015: Output Validation & Typed Handoffs**
  - Add output schema validation to Tool interface: validate ToolResult against expected schema
  - Inter-agent handoff validation: typed contracts between agents in orchestrator
  - Fail-fast on schema mismatch instead of propagating malformed data
  - Priority: P1 (prevents cascading failures in multi-agent chains)

- [ ] **TODO-016: Sub-agent Model Cost Optimization**
  - Support per-agent model override (already in AgentProfile.modelOverride — verify it's wired through)
  - Default strategy: main agent on capable model (Opus), sub-agents on efficient model (Sonnet)
  - Add cost tracking per agent to CostTracker for visibility
  - Priority: P1 (direct cost savings)

- [ ] **TODO-017: Eval Gates & Quality Scoring**
  - Add OutputEvaluator interface: score agent outputs against quality criteria
  - Configurable gate: block/warn/log when score below threshold
  - Integrate with AgentObserver.onAgentComplete for non-intrusive scoring
  - Track quality metrics over time for regression detection
  - Priority: P2 (production readiness)

- [ ] **TODO-018: Context Drift Detection**
  - Monitor for context drift during multi-step reasoning (not just token exhaustion)
  - Track key facts/goals established early in conversation; alert when they're lost after compaction
  - Implement "anchor facts" that survive all compaction layers
  - Priority: P1 (addresses the #1 cause of enterprise AI failures)

---

## Architecture Principles Validated by External Research

The following Runner design decisions are validated as industry best practices:

1. **Reactive streaming (Flux<StreamEvent>)** — Claude Code uses AsyncGenerator, OpenClaw uses block streaming. Reactor Flux is the Java-native equivalent and is a sound choice.
2. **Tool-first extensibility** — Universal pattern across all frameworks studied. Tool interface + registry is the canonical approach.
3. **4-layer Orchestrator** — Mirrors both Claude Code's agent management and OpenClaw's Primary/Subagent architecture. Well-structured.
4. **Outside-in TDD** — Claude Code's 98.4% deterministic infrastructure validates that the wiring/transmission-chain bugs caught by outside-in TDD are real and common.
5. **Events as first-class citizens** — All studied frameworks use typed event enums over string conventions.
6. **Dependency flows downward** — Clean module boundaries are universal in successful frameworks.

## Key Insight from Claude Code Analysis

> Only 1.6% of the codebase is AI decision logic. The other 98.4% is deterministic infrastructure — permission gates, context management, tool routing, and recovery logic.

This validates Runner's investment in infrastructure (ToolRegistry, ContextCompactor, ScopedToolRegistry, InterruptibleRun) but suggests the biggest gains come from deepening that infrastructure (richer compaction, smarter permissions, better observability) rather than adding more AI-driven features.

---

## Next Analysis Sessions

- **2026-06-09**: Deep dive into Context Compaction (TODO-001) — design the 5-layer pipeline, benchmark token savings
- **2026-06-10**: Permission system design (TODO-002) — model after Claude Code's deny-first architecture
- **2026-06-11**: Subagent isolation models (TODO-004) — design WorktreeSubagent with git-worktree integration
