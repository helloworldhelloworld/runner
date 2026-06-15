# ADR-007: 风险等级跨 MCP 边界的传递

## Status

Accepted (2026-06-07)

## Context

R5 让 runner 通过 MCP 连 Pi 的 minion-body server，把 `move`/`turn_head` 等**物理动作**工具
接进 `ToolRegistry`。minion persona 用 `ToolPolicy.byRiskLevel(SYSTEM)` 收口（见
[ADR-006](006-minion-embodiment-architecture.md) / OrchestratorConfig）。

问题：`Tool.riskLevel()` 默认 `SAFE`，而 `McpToolWrapper`（包装远程 MCP 工具为 `Tool`）
**没有覆写它**。于是**所有远程工具一律被风险闸当作 SAFE**——这是 **fail-open**：

- 在 `byRiskLevel(SYSTEM)` 下：物理 `move` 仍放行（SYSTEM 全放），能用，但理由是错的；
- 一旦有人**收紧**到 `byRiskLevel(WRITE)` 想挡住物理动作，远程 `move`（物理上应是 SYSTEM）
  会被当 SAFE **错误放行**——朝危险方向失效。

MCP 协议本身**没有** risk 字段，但有两个可承载它的标准位：
`Tool.annotations`（`readOnlyHint`/`destructiveHint`…）与 `Tool._meta`（自由 KV）。

## Decision

`McpToolWrapper` 覆写 `riskLevel()`，从 MCP 工具定义恢复风险，优先级：

1. **`_meta` 显式风险**（key `com.lightweightai.kernel/riskLevel`，值 `SAFE`/`WRITE`/`SYSTEM`，
   大小写不敏感）——server 想精确声明时用这个，最高优先。
2. **标准 annotations 推导**（无显式 `_meta` 时）：
   - `readOnlyHint == true` → `SAFE`（只读/抓帧）
   - `destructiveHint == true` → `SYSTEM`（物理/不可逆副作用）
   - annotations 存在但两者都非 true → `WRITE`（有副作用、非破坏性，如改显示）
3. **兜底 `SAFE`**——无任何注解的旧 server **行为不变**（向后兼容）。

producer 侧（minion-body）据此声明：`look` → `readOnlyHint=true`（SAFE）；
`move`/`turn_head`/`wave`/`nod` → `destructiveHint=true`（SYSTEM）；`set_eyes` → annotations
存在但非只读非破坏（WRITE）。也可用 `_meta` 显式覆盖。

## Consequences

- **Positive**：风险闸对远程设备工具真正生效；收紧策略（`byRiskLevel(WRITE)`）能挡住物理动作——
  **fail toward 安全**，而非 fail-open。
- **Positive**：用 MCP 协议自带语义（annotations），不自造字段、不污染给 LLM 看的 description；
  `_meta` 仅作精确覆盖。
- **Additive-only**：只新增 `riskLevel()` 覆写 + 包私有映射辅助；不改任何对外签名
  （`McpToolWrapper` 是对外契约，见 CLAUDE.md "Public API Stability"）。无注解的旧 server 不受影响。
- **Trade-off**：风险语义跨仓约定（runner 映射规则 + minion-body 声明）需两边同步；本 ADR 即其单一出处。
- **Deferred**：物理安全反射仍按 ADR-006 D7 落设备端，不靠这条风险闸兜底。
