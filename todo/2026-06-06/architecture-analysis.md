# Architecture Analysis & TODO - 2026-06-06

## Research Sources

- **OpenClaw** (GitHub 247K stars): TypeScript AI agent framework, originally "Clawd", built on Pi Agent framework
- **Claude Code** architecture: arXiv 2604.14228 + npm source map leak (v2.1.88, March 2026), ~512K lines TS
- **Industry best practices**: LangGraph, AutoGen/AG2, CrewAI, OpenHands, SWE-agent, Aider, Magentic-One, Spring AI, LangChain4j

---

## Part 1: External Architecture Insights

### 1.1 Claude Code Key Findings

The core insight: **only 1.6% of Claude Code is AI decision logic; 98.4% is deterministic infrastructure** (permission gates, context management, tool routing, recovery logic). The design philosophy is "minimal scaffolding, maximal operational harness."

| Component | Claude Code Approach | Runner Current State |
|---|---|---|
| Agent Loop | Single-threaded while-loop (~88 lines), flat message history | AgentLoop + ToolCallingLoop, similar pattern |
| Permission System | 7 modes + ML classifier (deny-first, graduated trust) | No permission system |
| Context Compaction | 3-tier (Micro/AutoCompact/SessionMemory), proactive at ~167K/200K | Snip(3) + Micro(2000), 2-layer only |
| Prompt Caching | Entire harness designed around cache hits; SEVs on low hit rate | No cache-aware request ordering |
| Extensibility | 4 mechanisms: MCP, Plugins, Skills, Hooks | MCP + Tools + Skills (deprecated), no Hooks |
| Tool Execution | Diff-based editing, context-budget-aware, read-before-edit enforcement | Full output, no windowing, no read guard |
| Tool Deferral | MCP tools deferred until ToolSearch loads schema; preserves cache prefix | All tool schemas loaded upfront |
| State | Append-oriented JSONL session storage, CLAUDE.md hierarchy | MemoryProvider 3-layer, good |
| Subagents | Worktree isolation, coordinator mode, agent teams (2-16), dynamic workflows | SubagentRuntime with depth/concurrency control |
| Background Daemon | KAIROS: always-on with periodic ticks, 15s budget, append-only audit log | No background execution |

### 1.2 OpenClaw Key Findings

| Feature | OpenClaw Approach | Runner Relevance |
|---|---|---|
| Heartbeat System | Periodic agent turns without user input (default 30min) | Missing - could enable autonomous monitoring agents |
| Workspace-as-Config | SOUL.md, AGENTS.md, TOOLS.md, HEARTBEAT.md plain text files | Partially done via AgentProfile, but not file-driven |
| Skill System | Modular SKILL.md files with YAML frontmatter, agent can write own skills | Skills exist but are @Deprecated |
| Channel Abstraction | 20+ messaging channels (WhatsApp, Telegram, Slack, etc.) | Only WebSocket/SSE |
| Hub-and-Spoke Gateway | Single Gateway as control plane routing to isolated agent sessions | Gateway exists but simpler |
| Pi Agent Framework | Minimal 4-tool coding agent (read, bash, edit, write) at core | Tool-rich but no "core 4" distinction |

### 1.3 Industry Best Practices Summary

| Pattern | Leaders | Runner Status | Priority |
|---|---|---|---|
| Event Sourcing | OpenHands, LangGraph | Not implemented | HIGH |
| Checkpoint/Resume | LangGraph, Claude Code | InterruptibleRun (partial) | HIGH |
| Dual-Loop Planning | Magentic-One (outer task ledger + inner progress ledger) | Single loop only | MEDIUM |
| Graph-Based Orchestration | LangGraph | Tree-based only | LOW (current approach is fine) |
| A2A Protocol | Google ADK, Spring AI | Not implemented | MEDIUM |
| Tool Output Windowing | SWE-agent (ACI design) | Not implemented | HIGH |
| Proactive Compaction | Claude Code (3-tier pipeline) | Reactive only | HIGH |
| Prompt Cache-Aware Ordering | Claude Code (SEV on low hit rate) | Not implemented | HIGH |
| Circuit Breaker / Resilience | Industry standard | Not implemented | HIGH |
| PageRank Context Selection | Aider (tree-sitter + PageRank) | Not implemented | MEDIUM |
| Stateless MCP | MCP 2026-07-28 spec RC | Session-based | MEDIUM |
| Permission System | Claude Code (6 modes + ML) | Missing | HIGH |
| Tool Deferral | Claude Code (ToolSearch on demand) | All loaded upfront | HIGH |
| Read-Before-Edit | Claude Code (Edit requires prior Read) | Not enforced | MEDIUM |
| Background Daemon | Claude Code KAIROS / OpenClaw heartbeat | Not implemented | MEDIUM |
| Dynamic Workflows | Claude Code (JS orchestration scripts) | Static routing only | MEDIUM |

---

## Part 2: Current Architecture Strengths

Things the runner does well (keep these):

1. **Reactive Streaming** - `Flux<StreamEvent>` with `EventType` enum is well-aligned with industry consensus
2. **ScopedToolRegistry** - Deny-before-allow model matches industry best practice
3. **Multi-Agent Orchestration** - 4-layer pattern (Profile/Registry/Orchestrator/SubagentRuntime) is production-grade
4. **Tool-First Extensibility** - Tool interface + ToolRegistry + MCP bridge is the universal pattern
5. **Memory Isolation** - SessionKey namespace prevents cross-agent contamination
6. **InterruptibleRun** - Full-duplex interrupt/resume is ahead of most frameworks
7. **CostTracker** - Token budget enforcement per request chain
8. **Cascade Stop** - Recursive child process termination
9. **TDD Rules** - Outside-in testing discipline with transmission-chain tests
10. **Clean Dependency Graph** - Strictly downward, no circular dependencies

---

## Part 3: TODO Items (Prioritized)

### P0 - Critical Architecture Gaps

#### TODO-001: Permission System
**Gap**: No tool execution permission system. Every tool call executes unconditionally.
**Reference**: Claude Code has 7 permission modes + ML classifier. OpenClaw inherits Pi Agent's permission model.
**Why**: Security baseline. Without this, deploying to production exposes arbitrary code execution.
**Scope**:
- [ ] Define `PermissionMode` enum: `INTERACTIVE`, `ACCEPT_EDITS`, `PLAN_ONLY`, `AUTO`, `DENY_DEFAULT`, `BYPASS`
- [ ] Create `PermissionGate` interface with `boolean allow(Tool, Map<String,Object> args)` 
- [ ] Wire PermissionGate into ToolExecutor before execution
- [ ] Tool-level permission declarations: `filesystemAccess`, `networkAccess`, `codeExecution`, `dataScope`
- [ ] User confirmation flow via StreamEvent (new `PERMISSION_REQUEST` / `PERMISSION_RESPONSE` event types)
- [ ] Allowlist/denylist configuration per AgentProfile

#### TODO-002: Proactive Multi-Stage Context Compaction
**Gap**: Current Snip(3)+Micro(2000) is a 2-stage reactive compactor. No proactive trigger, no budget awareness.
**Reference**: Claude Code uses 5-layer pipeline triggered at ~92% capacity. BATS pattern uses 4 spending regimes.
**Why**: Context drift causes 65% of enterprise AI failures. Proactive compaction prevents quality degradation.
**Scope**:
- [ ] Add `ContextBudgetTracker` with 4 regimes: `HIGH` (0-60%), `MEDIUM` (60-80%), `LOW` (80-92%), `CRITICAL` (92%+)
- [ ] Each regime triggers different compaction aggressiveness
- [ ] Add new compaction stages:
  - Stage 1: Tool output truncation (always active)
  - Stage 2: SnipCompactor - remove old tool messages (current, keep)
  - Stage 3: SummaryCompactor - LLM-powered summarization of old conversation segments
  - Stage 4: MicroCompactor - truncate remaining large messages (current, keep)
  - Stage 5: EmergencyCompactor - aggressive summarization, keep only system + last 2 turns
- [ ] Configurable threshold percentages per AgentProfile
- [ ] Metrics: track compaction frequency, token savings, quality impact

#### TODO-003: Tool Output Windowing (ACI Design)
**Gap**: Tool outputs go directly into context with no size control. Large file reads, search results, command outputs can flood the context window.
**Reference**: SWE-agent's ACI principle: "LLMs need interfaces designed for them." Cap search results (max 50), windowed file viewing, lint before edit.
**Why**: Same GPT-4 scores 2x on SWE-Bench with ACI vs raw bash.
**Scope**:
- [ ] Add `ToolOutputPolicy` interface: `ToolResult constrain(ToolResult raw, ContextBudgetTracker budget)`
- [ ] Default policies: max lines (configurable), max chars, search result cap
- [ ] Per-tool output policies (e.g., file read: windowed view; grep: max 50 matches)
- [ ] Budget-aware: more aggressive truncation when context budget is LOW/CRITICAL
- [ ] Truncated results include metadata: `[showing 50 of 1,234 results]`

#### TODO-004: Structured Error Recovery
**Gap**: Generic catch-and-retry on tool failures. No typed recovery strategies.
**Reference**: Production systems fail at 41-86.7% rates without deliberate fault tolerance. Best practice: 7 typed recovery strategies.
**Why**: Tool failures are the #1 source of agent loop breakage in production.
**Scope**:
- [ ] Define `RecoveryStrategy` enum: `RETRY`, `SKIP`, `REPLAN`, `SUBSTITUTE_TOOL`, `ESCALATE_FIDELITY`, `REGENERATE_PRIOR_STEP`, `ESCALATE`
- [ ] `ToolErrorHandler` interface selects strategy based on error type + context
- [ ] Circuit breaker per tool: after N failures in window, trip circuit (stop calling that tool)
- [ ] Self-healing: feed stack traces back into LLM context for self-correction
- [ ] Bulkhead isolation: tool failures in one domain don't cascade to others
- [ ] Fallback chains: alternative tools or cached responses

### P1 - Important Enhancements

#### TODO-005: Event Sourcing
**Gap**: StreamEvents are fire-and-forget. No replay, no time-travel debugging, no crash recovery.
**Reference**: OpenHands V1 SDK uses immutable event log as single source of truth. LangGraph uses checkpoints after each node.
**Why**: Enables replay, debugging, session recovery, and audit trail.
**Scope**:
- [ ] Make all StreamEvent instances immutable (already mostly true)
- [ ] Add `EventStore` interface: `append(StreamEvent)`, `replay(sessionKey, fromSequence)`
- [ ] Assign monotonic sequence IDs to events
- [ ] Implement `FileEventStore` and `SQLiteEventStore`
- [ ] Session recovery: on crash, replay event log to reconstruct state
- [ ] Debug tooling: replay a session step-by-step

#### TODO-006: Checkpoint/Resume for Long-Running Tasks
**Gap**: InterruptibleRun handles user-initiated interrupt/resume but not crash recovery or durable checkpoints.
**Reference**: LangGraph checkpoints after each node. Claude Code has append-oriented session storage.
**Why**: Long-running multi-step tasks (code migration, large refactor) need crash durability.
**Scope**:
- [ ] `Checkpoint` data class: agentState, messageHistory, pendingToolCalls, metadata
- [ ] Persist checkpoint after each ToolCallingLoop iteration
- [ ] Resume from checkpoint on restart
- [ ] Human-in-the-loop: pause at checkpoint, wait for user input, resume
- [ ] TTL on checkpoints (auto-cleanup stale sessions)
- [ ] Build on existing InterruptibleRun infrastructure

#### TODO-007: Lifecycle Hooks System
**Gap**: AgentObserver provides programmatic hooks but no declarative/configurable hook system.
**Reference**: Claude Code has PreToolUse/PostToolUse hooks configurable via settings. OpenClaw has BOOTSTRAP.md initialization sequence.
**Why**: Users need to inject custom logic (logging, security audit, cost control) without modifying core code.
**Scope**:
- [ ] Define hook points: `PRE_AGENT_START`, `POST_AGENT_START`, `PRE_TOOL_USE`, `POST_TOOL_USE`, `PRE_LLM_CALL`, `POST_LLM_CALL`, `ON_ERROR`, `ON_COMPLETE`
- [ ] Hook configuration in YAML/JSON (not just code)
- [ ] Shell command hooks (execute external commands at hook points)
- [ ] Hook ordering (priority-based)
- [ ] Hook result: `CONTINUE`, `ABORT`, `MODIFY` (can modify tool args or LLM messages)
- [ ] Built-in hooks: audit logging, cost tracking, permission enforcement

#### TODO-008: Skill System Revival
**Gap**: Current skill system is @Deprecated. PromptEngine has skill support but filesystem-loaded skills are deprecated.
**Reference**: OpenClaw has SKILL.md files with YAML frontmatter. Claude Code has slash commands. Both allow dynamic skill registration.
**Why**: Skills are the natural extension point for domain-specific agent capabilities.
**Scope**:
- [ ] Design new Skill format: Markdown with YAML frontmatter (name, triggers, priority, tools, systemPrompt)
- [ ] Skill discovery: scan `skills/` directory, MCP skill providers
- [ ] Skill marketplace: shared skill registry (like OpenClaw's ClawHub)
- [ ] Agent-writable skills: agent can create new skills during execution
- [ ] Deprecate old SkillRegistry, migrate to PromptEngine-based system
- [ ] Slash command support for interactive skill invocation

#### TODO-009: Heartbeat/Scheduled Execution
**Gap**: Agents are purely reactive (respond to user input only).
**Reference**: OpenClaw's heartbeat fires periodic agent turns (default 30min). Enables cron-triggered autonomous loops.
**Why**: Enables monitoring agents, scheduled tasks, proactive notifications.
**Scope**:
- [ ] `HeartbeatScheduler` with configurable interval per AgentProfile
- [ ] Heartbeat turns inject system message: "This is a scheduled check. Review your task list and act proactively."
- [ ] HEARTBEAT.md equivalent: define what the agent should check on each heartbeat
- [ ] Integration with SubagentRuntime: heartbeat can spawn subagents
- [ ] Quiet mode: heartbeat runs but only emits events if action is needed

### P2 - Medium Priority

#### TODO-010: Dual-Loop Planning (Task Ledger + Progress Ledger)
**Gap**: Single agent loop with no structured planning or progress tracking.
**Reference**: Magentic-One uses outer loop (task decomposition) + inner loop (step execution with self-reflection).
**Why**: Complex multi-step tasks benefit from explicit planning and progress tracking.
**Scope**:
- [ ] `TaskLedger`: facts, hypotheses, step plan, current step index
- [ ] `ProgressLedger`: per-step status, self-reflection after each step
- [ ] Planner agent: decomposes complex tasks into steps before execution
- [ ] Re-planning: when a step fails, update ledger and re-plan remaining steps
- [ ] Progress events: `PLAN_CREATED`, `STEP_STARTED`, `STEP_COMPLETED`, `PLAN_REVISED`

#### TODO-011: A2A Protocol Support
**Gap**: No cross-framework agent interoperability.
**Reference**: Google A2A protocol under Linux Foundation. Spring AI has A2A integration (Jan 2026).
**Why**: Enables runner agents to communicate with agents built in other frameworks.
**Scope**:
- [ ] Agent Card: JSON metadata for agent discovery (capabilities, endpoint, auth)
- [ ] A2A Task lifecycle: submitted, working, input-required, completed, failed, canceled
- [ ] A2A Message format: text parts, file parts, data parts
- [ ] Inbound: expose runner agents as A2A-compatible endpoints
- [ ] Outbound: call external A2A agents as if they were tools

#### TODO-012: Stateless MCP Transport
**Gap**: Current MCP integration is session-based (WebSocket transport with initialize handshake).
**Reference**: MCP 2026-07-28 spec RC goes fully session-less. No `Mcp-Session-Id`, no initialize handshake.
**Why**: Stateless transport enables load balancing, scaling, and proxy compatibility.
**Scope**:
- [ ] Implement Streamable HTTP transport (replaces SSE)
- [ ] Support stateless mode: each request stands on its own
- [ ] Tool-minted handles for state (tool returns a handle, model passes it back)
- [ ] OAuth 2.1 authentication (MCP June 2025 spec)
- [ ] Backward compatibility with existing session-based transports

#### TODO-013: Repository-Aware Context Selection
**Gap**: No automatic code context selection. Users must manually specify files.
**Reference**: Aider uses tree-sitter to parse symbols, builds reference graph, applies PageRank with chat-biased personalization.
**Why**: Automatic context selection dramatically improves coding agent effectiveness.
**Scope**:
- [ ] Tree-sitter integration for Java/TypeScript/Python symbol extraction
- [ ] Symbol reference graph construction
- [ ] PageRank scoring with personalization vector biased toward active files
- [ ] Automatic inclusion of top-N most relevant files in context
- [ ] Repository map generation (condensed symbol overview for LLM)

#### TODO-014: Channel Abstraction Layer
**Gap**: Only WebSocket and SSE transport. No messaging platform integration.
**Reference**: OpenClaw supports 20+ channels. Hub-and-spoke gateway routes to isolated sessions.
**Why**: Agents should be accessible from wherever users work (Slack, Teams, etc.).
**Scope**:
- [ ] `Channel` interface: `receiveMessage()`, `sendMessage()`, `getChannelType()`
- [ ] Channel plugin architecture (load dynamically)
- [ ] Built-in channels: WebSocket (existing), SSE (existing), REST API
- [ ] Plugin channels: Slack, Discord, Telegram (community-contributed)
- [ ] Channel-specific tool variants (e.g., Slack-specific actions)
- [ ] Message format normalization across channels

### P0 - Critical (Addendum from Claude Code Leak Analysis)

#### TODO-018: Prompt Cache-Aware Request Ordering
**Gap**: Runner sends LLM requests without considering prompt caching. No layered ordering.
**Reference**: Anthropic declares SEVs on low cache hit rates. The entire Claude Code harness is designed around this.
**Why**: Prompt caching can reduce latency by 80% and cost by 90% on cache hits.
**Scope**:
- [ ] Layer request: system prompt (stable) → tool definitions (stable) → project context (semi-stable) → conversation (volatile)
- [ ] Never modify system prompt or tool definitions mid-session
- [ ] State transitions as tools, not system prompt changes
- [ ] Track cache hit rate as a metric
- [ ] See `todo/2026-06-06/claude-code-deep-dive.md` for full details

### P1 - Important (Addendum)

#### TODO-019: Tool Definition Deferral
**Gap**: All tool schemas loaded into every LLM request. With MCP servers this bloats the prompt and breaks cache prefix.
**Reference**: Claude Code defers MCP tool schemas until ToolSearch loads them on demand.
**Scope**:
- [ ] `DeferredToolDefinition` — name + description only, no full schema
- [ ] ToolSearch mechanism to load schema on demand
- [ ] `alwaysLoad` flag for critical tools
- [ ] MCP output token limits (warn 10K, max 25K)

#### TODO-020: Ordered Tool Result Emission
**Gap**: Concurrent tool execution without guaranteed result ordering.
**Reference**: Claude Code `StreamingToolExecutor` uses `TrackedTool` state machine, emits in request order.
**Scope**:
- [ ] `TrackedToolCall` with states: QUEUED → EXECUTING → COMPLETED → YIELDED
- [ ] Emit results in request order regardless of completion order

### P2 - Medium (Addendum)

#### TODO-021: Read-Before-Edit Enforcement
**Gap**: No guarantee agent has read a file before editing it.
**Reference**: Claude Code's Edit tool requires prior Read in conversation + file unchanged on disk since.
**Scope**:
- [ ] Track "last read" timestamp per file in session
- [ ] Edit rejects if not read or if file changed since last read

#### TODO-022: Background Daemon Mode
**Gap**: Agents only run in foreground sessions.
**Reference**: Claude Code KAIROS (always-on daemon, periodic ticks, 15s budget, append-only logs). OpenClaw heartbeat.
**Scope**:
- [ ] `DaemonRuntime` with periodic tick scheduler
- [ ] Tick budget, append-only audit log, webhook subscriptions
- [ ] Subsumes TODO-009 (Heartbeat)

#### TODO-023: Dynamic Workflow Orchestration
**Gap**: Multi-agent orchestration is static (predefined routing).
**Reference**: Claude Code dynamically generates JS orchestration scripts for hundreds of parallel subagents.
**Scope**:
- [ ] Agent generates orchestration plans as structured data
- [ ] Workflow engine: parallel fan-out, sequential chains, conditional branches
- [ ] Each step maps to a subagent spawn

### P3 - Nice to Have

#### TODO-015: Agent Self-Improvement (Meta-Learning)
**Gap**: Agents don't learn from past sessions.
**Reference**: OpenClaw agents can write their own skills. Claude Code uses CLAUDE.md as persistent memory.
**Scope**:
- [ ] Post-session reflection: agent evaluates what worked/failed
- [ ] Automatic skill creation from successful multi-step patterns
- [ ] Persistent agent memory across sessions (beyond conversation history)

#### TODO-016: Observability & Tracing Improvements
**Gap**: Basic Tracer/SpanContext exists but no integration with standard observability.
**Reference**: Industry standard is OpenTelemetry for distributed tracing.
**Scope**:
- [ ] OpenTelemetry integration (traces, metrics, logs)
- [ ] Per-agent dashboards: token usage, tool call frequency, error rates
- [ ] Cost attribution per agent/session/user
- [ ] Trace visualization (agent loop iterations, tool calls, LLM latency)

#### TODO-017: Workspace-as-Configuration
**Gap**: Agent configuration is code-driven (AgentProfile Java class).
**Reference**: OpenClaw uses plain text files (SOUL.md, AGENTS.md). Claude Code uses CLAUDE.md.
**Scope**:
- [ ] Support loading AgentProfile from YAML/Markdown files
- [ ] Hot-reload on file change
- [ ] AGENTS.md: define agent identities and routing rules
- [ ] TOOLS.md: tool allowlist/denylist per workspace

---

## Part 4: Architecture Debt

### Technical Debt Items

1. **@Deprecated SkillRegistry** - Old filesystem-loaded skill system still in codebase. Either revive with new design (TODO-008) or remove entirely.
2. **@Deprecated InstructionRegistry** - Legacy instruction system. Remove and migrate to PromptEngine.
3. **Legacy Plugin/PluginFunction** - Still present. Enforce migration to Tool interface (ADR-002 already decided this).
4. **No integration tests against real MCP servers** - Only unit tests with mocks. Need faithful fake servers per integration-seam rules in CLAUDE.md.
5. **ContextCompactor has no budget awareness** - Compaction is structural (Snip/Micro) but doesn't consider how full the context window actually is.
6. **ToolCallingLoop error handling is generic** - `try/catch` around tool execution with generic error message back to LLM. No typed recovery.
7. **No tool output size limits** - A tool returning 100KB of text goes straight into messages with only MicroCompactor (2000 char) as backstop.
8. **AgentObserver hooks are code-only** - No declarative/configurable hook system.
9. **No audit trail** - Tool executions are not logged to a durable audit store.
10. **Gateway lacks rate limiting** - No per-user or per-session rate limiting.

---

## Part 5: Recommended Execution Order

### Phase 1: Foundation & Performance (Weeks 1-3)
- TODO-018: Prompt Cache-Aware Request Ordering (biggest ROI — 80% latency, 90% cost reduction)
- TODO-003: Tool Output Windowing (prevents context blowup)
- TODO-002: Proactive Context Compaction (critical for long sessions)

### Phase 2: Security & Reliability (Weeks 4-6)
- TODO-001: Permission System (security baseline)
- TODO-004: Structured Error Recovery (production stability)
- TODO-019: Tool Definition Deferral (scales to hundreds of MCP tools)

### Phase 3: Core Infrastructure (Weeks 7-10)
- TODO-005: Event Sourcing (enables everything else)
- TODO-006: Checkpoint/Resume (crash durability)
- TODO-020: Ordered Tool Result Emission

### Phase 4: Extensibility (Weeks 11-14)
- TODO-007: Lifecycle Hooks System
- TODO-008: Skill System Revival
- TODO-021: Read-Before-Edit Enforcement

### Phase 5: Interoperability (Weeks 15-18)
- TODO-012: Stateless MCP Transport
- TODO-011: A2A Protocol Support
- TODO-014: Channel Abstraction Layer

### Phase 6: Advanced (Weeks 19+)
- TODO-022: Background Daemon Mode (subsumes TODO-009)
- TODO-023: Dynamic Workflow Orchestration
- TODO-010: Dual-Loop Planning
- TODO-013: Repository-Aware Context Selection
- TODO-015: Agent Self-Improvement
- TODO-016: Observability
- TODO-017: Workspace-as-Configuration

---

## Supplementary Documents

- [claude-code-deep-dive.md](claude-code-deep-dive.md) — Detailed Claude Code architecture from leaked source analysis

## References

### Academic Papers
- [arXiv 2604.14228] Dive into Claude Code: The Design Space of Today's and Future AI Agent Systems
- [arXiv 2602.23193] ESAA: Event Sourcing for Autonomous Agents
- [arXiv 2405.15793] SWE-agent: Agent-Computer Interfaces Enable Automated Software Engineering
- [arXiv 2603.05344] Building Effective AI Coding Agents for the Terminal
- [arXiv 2604.03515] Inside the Scaffold: A Source-Code Taxonomy of Coding Agent Architectures
- [arXiv 2602.16666] Towards a Science of AI Agent Reliability

### Claude Code Sources
- Claude Code Official Docs: https://code.claude.com/docs/en/how-claude-code-works
- Claude Code Source Leak Analysis: https://claudefa.st/blog/guide/mechanics/claude-code-source-leak
- Claude Code Architecture Deep Dive: https://wavespeed.ai/blog/posts/claude-code-architecture-leaked-source-deep-dive/
- Claude Code Prompt Caching: https://code.claude.com/docs/en/prompt-caching
- VILA-Lab/Dive-into-Claude-Code: https://github.com/VILA-Lab/Dive-into-Claude-Code
- Claw Code (clean-room Rust rewrite): https://claw-code.codes/

### OpenClaw Sources
- OpenClaw GitHub: https://github.com/openclaw/openclaw
- OpenClaw Design Patterns (Ken Huang, 7-part series)
- OpenClaw Architecture Deep Dive: https://deepwiki.com/openclaw/openclaw/15.1-architecture-deep-dive
- OpenClaw Chinese docs: https://github.com/yeuxuan/openclaw-docs

### Framework & Protocol References
- MCP 2026-07-28 Release Candidate: https://blog.modelcontextprotocol.io/posts/2026-07-28-release-candidate/
- Spring AI 1.1 Recursive Advisors: https://spring.io/blog/2025/11/04/spring-ai-recursive-advisors/
- Spring AI A2A Integration: https://spring.io/blog/2026/01/29/spring-ai-agentic-patterns-a2a-integration/
- LangChain4j MCP Integration: https://github.com/langchain4j/langchain4j
- Aider Architecture Analysis: https://emsenn.net/library/domains/engineering/domains/tech/domains/computing/texts/aider-architecture-analysis/
- Context Engineering for AI Agents (Anthropic): https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- Building Agents with Claude Agent SDK: https://www.anthropic.com/engineering/building-agents-with-the-claude-agent-sdk
