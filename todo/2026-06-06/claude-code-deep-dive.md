# Claude Code Deep Dive - 2026-06-06

Source: npm source map leak (v2.1.88, March 31 2026) + arXiv 2604.14228 + official docs.

---

## 1. Source Code Scale

- 512K+ lines TypeScript across 1,906 files
- query.ts (main agent loop): 785 KB, the largest single file
- System prompt: ~4,000 tokens
- 43 built-in tools, 80+ slash commands, 44 feature flags
- UI: Custom React reconciler using Ink with game-engine-style rendering

## 2. Agent Loop Internals

The loop is ~88 lines in `AgentRuntime`. State is a single message array.

```
submitMessage() → AsyncGenerator<SDKMessage>
  → query()
    → queryLoop()
      → deps.callModel() → queryModelWithStreaming → Messages API
      → tool detection
      → StreamingToolExecutor (concurrent, ordered emission)
      → re-prompt loop
```

Key insight: `StreamingToolExecutor` wraps each tool call in a `TrackedTool` with states: **queued → executing → completed → yielded**. Results emit in request order even if tools finish out-of-order.

## 3. Prompt Caching Architecture (CRITICAL PATTERN)

**The entire harness is designed around prompt caching.** Anthropic runs alerts on cache hit rate and declares SEVs if too low.

Request ordering for maximum cache hits:
1. **System prompt layer** (rarely changes) — core instructions, tool definitions, output style
2. **Project context layer** — CLAUDE.md, auto memory, unscoped rules  
3. **Conversation layer** (changes every turn) — messages, responses, tool results

Cache-breaking vectors:
- Switching models or effort levels
- Connecting/disconnecting MCP servers
- Enabling/disabling plugins
- Compacting conversation
- Upgrading Claude Code

Design rules:
- Plan mode injects instructions as conversation messages (NOT system prompt changes) to preserve cache
- Tool definitions are kept static — state transitions modeled as tools (EnterPlanMode/ExitPlanMode) not tool set changes
- Each model+effort combination has its own cache

Cache TTL: 5min (API key), 1 hour (subscription).

## 4. Three-Tier Compaction (Corrected)

Not 5-layer as initially reported. Three distinct tiers:

| Tier | Name | Cost | Mechanism |
|---|---|---|---|
| 1 | MicroCompact | Zero API calls | Clears stale tool results locally, trims old outputs directly |
| 2 | Full Compact (AutoCompact) | One model call | Fires at ~167K tokens (200K window - 13K buffer). Generates up to 20K-token structured summary |
| 3 | Session Memory Compact | Zero API calls | Uses pre-extracted notes to skip summarization entirely |

High-value content survives compaction and is re-inserted as new messages:
- File attachments from recently accessed files
- Plans and skill definitions
- Tool definitions (especially MCP)

Circuit breaker: After 3 consecutive compression failures, stops retrying.

## 5. Permission System Details

Six modes: `default`, `acceptEdits`, `plan`, `auto`, `dontAsk`, `bypassPermissions`.

**Auto mode ML classifier**: A separate Sonnet 4.6 instance evaluates every tool call, asking:
1. Is this action dangerous?
2. Did the user authorize it?
3. Does the authorization cover the blast radius?

Two-stage evaluation: fast path (max_tokens=64), then chain-of-thought if inconclusive.

**Permission rule specifiers** (pattern-based):
```
Bash(npm run *)
Read(~/secrets/**)
Edit(/src/**)
WebFetch(domain:example.com)
```

Rule precedence: deny > ask > allow (always).

## 6. Hooks System

Six lifecycle hook points:

| Hook | When | Can Block |
|---|---|---|
| PreToolUse | Before tool execution | Yes |
| PostToolUse | After tool completion | No |
| PermissionRequest | When permission dialog appears | Yes (auto-allow/deny) |
| UserPromptExpansion | Slash command / MCP prompt expansion | No |
| SessionStart | Session initialization | No |
| Stop | Agent finishes | No |

Hooks are shell scripts. Input: JSON via POST with Content-Type: application/json. Non-2xx = non-blocking error.

## 7. Tool Deferral Pattern (for MCP)

On supported models, MCP tools are **deferred** — not loaded into system prompt until needed. This preserves the cache prefix.

Flow:
1. Tool schemas NOT included in initial system prompt
2. LLM sees tool names only (via ToolSearch description)
3. When LLM needs a tool, it calls `ToolSearch` 
4. ToolSearch loads full schema on demand
5. Schema injected into next turn

Exception: tools/servers marked `alwaysLoad` bypass deferral.

MCP output limits: warning at 10K tokens, hard max 25K tokens (`MAX_MCP_OUTPUT_TOKENS`).

## 8. Multi-Agent Architecture

### Coordinator Mode
`CLAUDE_CODE_COORDINATOR_MODE=1` — transforms single agent into coordinator spawning parallel workers. Workers request human approval through a mailbox system.

### Agent Teams (Experimental)
`CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` — 2-16 agents with:
- `TeamCreate` tool
- `SendMessage` tool for peer-to-peer messaging
- Shared task list at `~/.claude/tasks/{team-name}/`
- Three subagent execution modes:
  - **Fork**: inherits parent conversation
  - **Teammate**: named agents with message routing
  - **Worktree**: isolated git worktree copy

### Dynamic Workflows
Claude dynamically writes JavaScript orchestration scripts that run tens to hundreds of parallel subagents. Subagents inherit parent's tool allowlist and run in acceptEdits mode.

## 9. KAIROS (Background Daemon)

Named after the Greek concept of "the right moment." An always-on background daemon:
- Receives periodic tick prompts, decides independently whether to act
- Continues running when laptop is closed
- Maintains session state across restarts
- 15-second blocking budget to prevent resource monopolization
- Append-only logging with daily logs the agent cannot self-erase
- GitHub webhook subscriptions
- Background daemon workers on 5-minute cron refresh

This is essentially OpenClaw's heartbeat pattern, but more sophisticated.

## 10. Edit Tool Invariants

The Edit tool enforces a **read-before-edit** requirement:
- Claude must have read the file in the current conversation
- The file must not have changed on disk since that read
- This prevents blind edits and merge conflicts

## 11. Memory Architecture

Three layers:
1. **In-context memory** — active conversation window
2. **External file memory** — indexed by `memory.md` (pointer index, not storage)
3. **Project-level static config** — CLAUDE.md hierarchy

CLAUDE.md hierarchy (most specific wins):
```
/etc/claude-code/CLAUDE.md     (global)
~/.claude/CLAUDE.md            (user)
CLAUDE.md                      (project root)
.claude/rules/*.md             (project rules)
CLAUDE.local.md                (private, gitignored)
```

Session persistence: JSONL format, supports `--continue` and `--resume`.

---

## New TODOs Identified from This Research

### TODO-018: Prompt Cache-Aware Request Ordering (P0)
**Gap**: Runner sends LLM requests without considering prompt caching. No layered ordering of system prompt → project context → conversation.
**Reference**: Anthropic declares SEVs on low cache hit rates. The entire Claude Code harness is designed around this.
**Why**: Prompt caching can reduce latency by 80% and cost by 90% on cache hits. Without cache-aware ordering, every turn pays full price.
**Scope**:
- [ ] Layer request construction: system prompt (stable) → tool definitions (stable) → project context (semi-stable) → conversation (volatile)
- [ ] Never modify system prompt or tool definitions mid-session to preserve cache prefix
- [ ] State transitions as tools (e.g., mode changes) rather than system prompt modifications
- [ ] Track cache hit rate as a metric
- [ ] Per-model cache management (different models = different cache keys)

### TODO-019: Tool Definition Deferral (P1)
**Gap**: All tool schemas loaded into every LLM request upfront. With MCP servers adding many tools, this bloats the system prompt and breaks cache prefix.
**Reference**: Claude Code defers MCP tool schemas until `ToolSearch` is called. Only tool names visible initially.
**Why**: Reduces system prompt size, preserves cache prefix, allows scaling to hundreds of MCP tools.
**Scope**:
- [ ] `DeferredToolDefinition` — name + description only, no schema
- [ ] `ToolSearch` mechanism to load full schema on demand
- [ ] `alwaysLoad` flag on critical tools that must be in every request
- [ ] Lazy schema injection on first use
- [ ] MCP output token limits (warn at 10K, hard max 25K)

### TODO-020: Ordered Tool Result Emission (P1)
**Gap**: Concurrent tool execution exists but result ordering is not guaranteed to match request order.
**Reference**: Claude Code's `StreamingToolExecutor` uses `TrackedTool` with states (queued → executing → completed → yielded) and emits in request order.
**Why**: Deterministic output ordering makes debugging easier and ensures consistent LLM re-prompting.
**Scope**:
- [ ] `TrackedToolCall` with state machine: QUEUED → EXECUTING → COMPLETED → YIELDED
- [ ] Emit results in request order regardless of completion order
- [ ] Integrate with StreamEvent for progress tracking per tool

### TODO-021: Read-Before-Edit Enforcement (P2)
**Gap**: No guarantee that the agent has read a file before editing it. Blind edits can corrupt files.
**Reference**: Claude Code's Edit tool requires a prior Read in the conversation and checks that the file hasn't changed on disk since.
**Scope**:
- [ ] Track "last read" timestamp per file path in agent session
- [ ] Edit tool rejects if file not read in current session
- [ ] Edit tool rejects if file modified on disk since last read
- [ ] Reduces merge conflicts and corruption in multi-agent scenarios

### TODO-022: Background Daemon Mode (P2)
**Gap**: Agents only run in foreground sessions. No persistent background execution.
**Reference**: Claude Code's KAIROS — always-on daemon with periodic ticks, 15-second blocking budget, append-only logging. OpenClaw's heartbeat at 30min intervals.
**Why**: Enables monitoring, CI/CD watching, proactive notifications without user interaction.
**Scope**:
- [ ] `DaemonRuntime` — long-lived process with periodic tick scheduler
- [ ] Tick budget (max execution time per tick, e.g., 15 seconds)
- [ ] Append-only audit log (agent cannot delete its own logs)
- [ ] Webhook subscriptions (GitHub events, etc.)
- [ ] Graceful shutdown and state persistence across restarts
- [ ] Subsumes TODO-009 (Heartbeat/Scheduled Execution)

### TODO-023: Dynamic Workflow Orchestration (P2)
**Gap**: Multi-agent orchestration is static (predefined AgentProfile routing). No dynamic workflow generation.
**Reference**: Claude Code dynamically writes JavaScript orchestration scripts that spawn tens to hundreds of parallel subagents.
**Why**: Complex tasks need dynamic decomposition — the agent should be able to design and execute its own workflow.
**Scope**:
- [ ] Agent can generate orchestration plans as structured data (not code scripts)
- [ ] Workflow engine executes plans: parallel fan-out, sequential chains, conditional branches
- [ ] Each workflow step maps to a subagent spawn
- [ ] Workflow state tracking and visualization via StreamEvents
- [ ] Inherits parent agent's permission model
