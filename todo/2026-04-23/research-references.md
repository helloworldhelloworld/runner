# Research References

Date: 2026-04-23

## OpenClaw Architecture

- [GitHub Repository](https://github.com/openclaw/openclaw) — MIT License, 100K+ stars
- [Architecture Docs](https://docs.openclaw.ai/concepts/architecture) — Gateway + Agent Loop + Event System
- [Agent Loop Design](https://docs.openclaw.ai/concepts/agent-loop) — 序列化 per-session lane
- [Multi-Agent Routing](https://docs.openclaw.ai/concepts/multi-agent) — sessions_spawn + per-agent workspace
- [Memory Config](https://docs.openclaw.ai/reference/memory-config) — Hybrid 70% vector + 30% BM25
- [Design Patterns (Ken Huang)](https://kenhuangus.substack.com/p/openclaw-design-patterns-part-1-of) — SOUL.md / AGENTS.md / workspace-first

### OpenClaw vs runner Key Differences

| Dimension | OpenClaw | runner |
|-----------|----------|--------|
| Runtime | Node.js single process | Spring Boot + Vert.x |
| Identity | SOUL.md (file-first) | AgentProfile (code-first) |
| Memory | SQLite + FTS5 + embedding ext | SQLite + BM25 + vector index |
| Sandboxing | Docker per-session | None (CLI tools unsandboxed) |
| Tool extension | MCP primary + Skills (SKILL.md) | MCP + SPI + Dynamic JAR + CLI + Annotation |
| Streaming | WebSocket JSON frames | WebSocket + SSE + Reactive Flux |

---

## Claude Code Architecture

- [arXiv Paper: Dive into Claude Code (2604.14228)](https://arxiv.org/abs/2604.14228) — 学术分析
- [Official Docs: How Claude Code Works](https://code.claude.com/docs/en/how-claude-code-works) — Agent loop + tool system
- [Permission Model](https://code.claude.com/docs/en/permissions) — 7-mode graduated trust
- [Auto Mode (Anthropic Blog)](https://www.anthropic.com/engineering/claude-code-auto-mode) — ML classifier for permissions
- [Context Compaction Analysis](https://finisky.github.io/en/claude-code-context-compaction/) — 5-layer pipeline
- [Compression Pipeline Deep Dive](https://harrisonsec.com/blog/claude-code-context-engineering-compression-pipeline/)
- [Inside Claude Code (Victor Dibia)](https://newsletter.victordibia.com/p/inside-claude-code)

### Claude Code vs runner Key Differences

| Dimension | Claude Code | runner |
|-----------|-------------|--------|
| Core loop | Single-threaded while-loop + async generator | Reactive Flux pipeline |
| Compaction | 5-layer cascade (Budget→Snip→Micro→Collapse→Auto) | 2-layer (Snip + Micro) |
| Permissions | 7-mode (plan→auto→bypass) + ML classifier | Static allow/deny list |
| Tool execution | Streaming overlap (start during LLM generation) | Wait for LLM_COMPLETE then execute |
| Subagent isolation | Git worktree per subagent | Session key namespace only |
| Context % | 98.4% deterministic infra, 1.6% AI logic | Similar ratio |

---

## Community Best Practices (2025-2026)

### Agent Loop & Framework Design
- [Letta: Rearchitecting Agent Loop](https://www.letta.com/blog/letta-v1-agent) — MemGPT → Letta v1, OS-inspired tiered memory
- [Oracle: AI Agent Loop Architecture](https://blogs.oracle.com/developers/what-is-the-ai-agent-loop-the-core-architecture-behind-autonomous-ai-systems)
- [Fordel Studios: State of AI Agent Frameworks 2026](https://fordelstudios.com/research/state-of-ai-agent-frameworks-2026) — LangGraph v1, CrewAI, AutoGen comparison

### Context Engineering
- [Anthropic: Effective Context Engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents) — 压缩 + 笔记 + 隔离三大技术
- [Claude Platform Cookbook: Context Engineering Tools](https://platform.claude.com/cookbook/tool-use-context-engineering-context-engineering-tools)
- [JetBrains Research: Smarter Context Management](https://blog.jetbrains.com/research/2025/12/efficient-context-management/)

### Multi-Agent
- [Google Cloud: Design Patterns for Agentic AI](https://docs.cloud.google.com/architecture/choose-design-pattern-agentic-ai-system)
- [Azure: AI Agent Orchestration Patterns](https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/ai-agent-design-patterns) — Supervisor / Hierarchical / Adaptive
- [Beam.ai: 6 Multi-Agent Patterns for Production](https://beam.ai/agentic-insights/multi-agent-orchestration-patterns-production)

### Protocols
- [MCP Specification 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25)
- [MCP 2026 Roadmap](https://blog.modelcontextprotocol.io/posts/2026-mcp-roadmap/) — 水平扩展、企业 auth、审计
- [Google A2A Protocol](https://developers.googleblog.com/en/a2a-a-new-era-of-agent-interoperability/) — Agent Card + task lifecycle

### Observability
- [OpenTelemetry: GenAI Agent Spans](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/) — 标准化 span conventions
- [Sentry: AI Agent Observability Guide](https://blog.sentry.io/ai-agent-observability-developers-guide-to-agent-monitoring/)

### Testing
- [Block Engineering: Testing Pyramid for AI Agents](https://engineering.block.xyz/blog/testing-pyramid-for-ai-agents) — L1 deterministic / L2 quality / L3 E2E
- [InfoQ: Docker Cagent Deterministic Testing](https://www.infoq.com/news/2026/01/cagent-testing/) — Record & Replay cassettes
- [DeepEval: AI Agent Evaluation](https://deepeval.com/guides/guides-ai-agent-evaluation)

### Resilience
- [AWS: Build Resilient Generative AI Agents](https://aws.amazon.com/blogs/architecture/build-resilient-generative-ai-agents/)
- [Anthropic: Effective Harnesses for Long-Running Agents](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents) — Checkpoint + two-fold pattern

### Anthropic Agent SDK
- [Claude Agent SDK (NPM)](https://www.npmjs.com/package/@anthropic-ai/claude-agent-sdk)
- [Anthropic: Managed Agents](https://www.anthropic.com/engineering/managed-agents) — 2026.4 announced
- [InfoQ: Anthropic Managed Agents](https://www.infoq.com/news/2026/04/anthropic-managed-agents/)
