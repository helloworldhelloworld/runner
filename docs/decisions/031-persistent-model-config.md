# ADR-031：模型配置预设持久化与原子切换

- 日期：2026-09-12
- 状态：Accepted

模型配置预设保存到可配置的本地 JSON 文件，更新使用临时文件和原子替换。启动时恢复预设元数据，
运行中的 provider 仍通过 `DynamicLLMProvider` 原子替换。API key 只在进程内保留，持久文件只保存
provider、model、baseUrl 等非秘密配置；应用配置或环境变量继续提供凭据。
