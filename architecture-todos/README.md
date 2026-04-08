# Architecture Todos

按日持续分析 runner 架构与业界最佳实践的差距，并沉淀优化 todo。

## 目录约定

- 每日一个文件：`YYYY-MM-DD.md`
- 模板章节：
  1. 外部参照物要点速览（Claude Code / OpenClaw / 其它）
  2. 现状架构痛点盘点（P0 / P1 / P2 三级）
  3. 本日动作清单
  4. 参考来源

## 收敛规则

- 已落地的 todo 迁入 `docs/decisions/` 形成正式 ADR。
- 已放弃的条目用 `~~strikethrough~~` 留痕，不删除。
- 跨日延续的 todo 在新文件顶部"延续条目"章节中引用旧文件。

## Trigger

- 每日至少一次增量分析。
- 外部重要变更（Claude Code 新版、OpenClaw release、相关 writeup）→ 立刻新增条目。
- 代码库出现架构性偏离（循环依赖、硬编码扩展点、状态泄露）→ 立刻记录。
