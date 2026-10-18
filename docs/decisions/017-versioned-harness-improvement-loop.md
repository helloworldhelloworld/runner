# ADR-017：版本化运行装配与证据驱动的改进闭环

状态：Proposed（2026-09-06）。受控候选/证据工作流已按 ADR-021 实施；生产装配与发布闭环尚未完成。

## Context

runner 已有 AgentProfile、Tool/Provider SPI、trace/span、Skill 轨迹萃取、测试和版本发布组件。
当前仍缺配置在 native 运行中的统一消费、被测内容与发布版本的绑定，以及反馈到候选验证的闭环。
正常 `end_turn`、工具无错误或管理页显示 active，各自只能证明局部状态。

[OpenAI Agent Improvement Loop](https://developers.openai.com/cookbook/examples/agents_sdk/agent_improvement_loop)
提供从执行记录、反馈、评测到实施交接的参考。
[DeepSeek Harness 架构](https://github.com/deepseek-ai/deepseek-harness/blob/d347e703908d0406b7a7ef80e3a0e594d86b2215/docs/architecture.md)
提供能力组合与生命周期边界的参考。两者在本项目中对应运行装配和离线改进两项职责，
详细比较、现状依据及拟议验收见[主方案](../design/runner-minion-system-evolution.md#114-持续改进从运行证据到下一版可验证变更)。

## Proposed decision

1. 保留 native 主线、向下依赖、`agent-web` 唯一 Spring 装配、Tool 和既有公开 SPI。
   记录实际生效的 Provider、工具、存储、策略、后处理及配置来源；人格 Profile 与部署装配分别建模。
   不以迁移 Cordis、Agents SDK 或重启 legacy Plugin 扩展作为前提。
2. 共享组件与 run 局部绑定分别持有。observer、订阅和会话工具的注册/释放有唯一责任方；
   清理幂等、可等待、按实例身份执行。运行使用固定配置快照；权限撤销和设备失联即时检查。
3. 会话事实、实时输出和诊断 trace 各自保留语义，通过运行与配置标识关联。
   重建模型请求需要提示、工具定义、消息及配置依据，不能只保留最终答案。
   记录播放和工具执行事实仍服从 ADR-016 的所有权及数据保留边界。
4. 离线改进读取成功、失败、取消等样本，分别记录人工反馈与模型评论，产生候选用例和变更。
   Skill 萃取结果进入候选流程；候选可以涉及提示、工具、流程或验证器，变更类型决定发布路径。
   分析失败不得阻塞当前对话或直接覆盖其运行配置。
5. 区分旧 trace 评分与实际候选运行。复现/开发/独立验收集分别版本化；协议、授权、取消及
   下游载荷采用确定性断言，内容质量可使用校准评分，真实声学与设备继续单独验收。
6. 候选以完整 harness 为单位，覆盖 Prompt/Profile/Skill、Tool/权限、Provider/模型参数、路由/编排、
   上下文与记忆策略、输出要求、验证器及相关运行时代码。Skill 不是顶层发布与评测单位。
   发布绑定候选内容摘要、代码/构建/未提交补丁、配置、用例/评分器版本、基线/候选结果及回退版本。
   修改候选后旧报告不能沿用；下一轮实际消费者确认采用被测快照才算生效。
   评分规则改变单独记录，关键契约和目标改进均通过后按相应发布策略切换。
7. 初期用批处理/CI 和现有存储复用证据与交接产物。Promptfoo、HALO、Codex 等为可选适配，
   kernel 仅提供通用观察和执行端口；具体持久格式、接口与调度在实施时细化。

## Alternatives and trade-offs

直接把成功轨迹注册成活跃 Skill 实现简单，但正常结束不足以证明正确性、适用范围或泛化。
单纯增加观测也不会保证问题被转成回归。版本化候选流程增加存储与评测成本，换来可解释的
变更依据和可检查的生效结果；先以少量既有失败案例验证价值，再扩大自动化。

完整移植 DSH 的动态插件体系会引入新的运行时与兼容边界。近期先用已有 Java/Python 接口
明确装配和所有权；若后续需要外部大脑，按 ChatHandler 的真实会话、取消和工具契约验证。
资源释放和版本回退均不撤销已发生的外部副作用。

## Acceptance before implementation is considered complete

- 并发会话的工具、observer、配置和清理互不串扰，所有实际下游载荷归属正确。
- 一次失败有 trace/反馈/用例引用；基线先失败，候选通过目标场景及独立关键回归。
- 修改已测试草稿后，旧结果不能满足新候选的发布门槛。
- 发布 v2 后下一轮真实 Provider 采用 v2；注册或消费失败不得只留下 active 状态。
- 旧 trace 评分、模拟设备结果和实机结果分别标注，报告能定位到实际被测版本。
- 控制事件保持显式类型和 factory；公开 Tool、ToolSourceProvider、MCP API 增量兼容。

## Implementation status

2026-09-06：完成原文及代码静态核对、主方案更新、工作包 I/J 和续接记录。
进一步形成[完整运行框架的落地设计](../design/runner-improvement-loop-implementation.md)，明确候选、证据、评测和发布的内容绑定。
ADR-020 取消链作为首个系统级样本。[ADR-021](021-frozen-candidate-evidence-bundle.md) 已实现仓库级候选清单、
评测内容版本、独立源码副本执行、实际下游证据、报告校验和原候选 replay；仅覆盖受控开发/CI 验收。
生产装配快照、通用在线轨迹采集、候选发布与下一次实际消费、自动分析建议仍待实施。
I/J 可以从受控开发样本独立推进；接入多用户线上轨迹前需完成 A/D 身份与存储作用域。
后续以[实施进展与续接点](../design/runner-minion-system-evolution.md#124-2026-09-06-实施进展与续接点)继续。
