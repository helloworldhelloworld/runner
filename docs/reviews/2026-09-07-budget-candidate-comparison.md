# 预算回调缺陷：冻结候选的实际比较

本次以运行框架代码缺陷完成一次可核验的改进闭环：AgentLoop 的旧回调入口绕过工具循环，
Gateway、Orchestrator 和 Agent 适配器丢失预算停止原因。验收固定后，代码修复消除了四项
失败。它不是 Skill 发布样本，也不是对整套系统或模型回答质量的评测结论。

## 实际执行与失败记录

| 执行 | 通过 | 失败 | 结果 |
|---|---:|---:|---|
| 修复前冻结候选 | 21 | 4 | 工具循环被绕过；三个回调响应丢失 budget_exhausted/usage |
| 修复后首次执行 | 24 | 1 | 四项目标缺陷通过；旧取消验收在首次模型请求前超过自设 5 秒期限 |
| 修复后原样 replay | 25 | 0 | 同一源码及评测，无修改地重跑全部 25 项通过 |

首次比较的 `regressed` 结果保留，不能删除失败后只展示成功。第二次比较报告为 `improved`，
含四项 fixed 和 21 项 unchanged。首次请求超时说明这组验收仍受冷启动/本机负载影响，不能
据单次成功声称稳定性改善；期间机器时钟跳跃约半小时，Maven 总耗时也不能作为性能指标。

## 内容标识

| 对象 | SHA-256 |
|---|---|
| 修复前候选 | `638e023aa9d9d72458ff0782730a1fce46fc9efff5f4b184a713bf12ed6ab589` |
| 修复后候选，两次执行相同 | `404e8a2f39563641c8dc603dac27eacea04a5ec298bd3fafa2281c000b794719` |
| 共同评测 | `00043748b7e0b21f07ebe94c514e068ed34884ebc6a365e419d6f16c9a34f1d3` |
| 修复前报告 | `5e83b5e4aba2bc489c0680ae0b3ddafbb208d02b166219c6619ca6431b8618ed` |
| 修复后 replay 报告 | `714e6f5c0bd2a030adea3722f79b6849e25d461752ef5d81a99eab4326c82039` |
| 第二次比较 | `37079a3931b1398b01f959f0f6122795c30dce94fe076a2a59a540f2aa6047be` |

定义在 [evals/budget-callback.json](../../evals/budget-callback.json)。证据记录实际 HTTP 请求、
停止原因、请求次数和客户端 delta；认证字段在导出前去除。额外回归包括父子取消、异步任务、
共享预算、未知用量、并发预留和实际输出限制。评测期间没有修改测试源码、定义或执行脚本。

## 本机证据与复现

完整快照和报告在以下本机目录；`/tmp` 不保证跨主机或长期保存，本文的摘要不能替代原始包。

- baseline：`/tmp/runner-budget-callback-before-20260907`
- candidate 首次：`/tmp/runner-budget-callback-after-20260907`
- candidate replay：`/tmp/runner-budget-callback-after-replay-20260907`
- 首次比较：`/tmp/runner-budget-callback-comparison-first-20260907`
- 第二次比较：`/tmp/runner-budget-callback-comparison-verified-20260907`

使用对应包内的冻结脚本 verify/replay，以新目录保存执行。Java 环境采用
`JDK_JAVA_OPTIONS='-Xmx384m -XX:ActiveProcessorCount=2'`，MAVEN_OPTS 同样限制资源。
后续源码已经继续变化，当前工作区不能冒充上述修复后候选；新工作需要重新 capture。

## 与参考架构的对应

[OpenAI improvement loop](https://developers.openai.com/cookbook/examples/agents_sdk/agent_improvement_loop)
关注整个 harness 的可观察问题、固定评测、候选执行、比较和分析交接。本次修复对象覆盖
Agent 执行与协议适配代码，并未限定于 Skill。本地脚本实现受控候选流程，没有声称已运行
HALO、Promptfoo 或云端自动优化。

[DSH session](https://github.com/deepseek-ai/deepseek-harness/blob/d347e703908d0406b7a7ef80e3a0e594d86b2215/docs/subsystems/session.md)
对缺失用量与失败尝试的区分用于 ADR-026 的账目语义；预算停止不伪造成功模型文本。
线上主体范围内的持久执行证据、反馈索引、完整运行框架发布和恢复仍是后续工作。

本次冻结评测只构建 agent-kernel，未证明 Web、Python 配套源码、全部 13 模块或实机通过。
这些部分的后续进展统一记录在[完整实施清单](../design/runner-completion-plan.md)。
