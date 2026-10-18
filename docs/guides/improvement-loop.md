# 运行框架候选与验收证据包

已实现并通过自动化验收；设计边界见 [ADR-021](../decisions/021-frozen-candidate-evidence-bundle.md)。本工具面向可信本地开发和 CI，
覆盖整套仓库源码与受控 native 验收；线上证据采集、候选发布和自动优化尚未接入。

## 执行首个系统样本

在 runner 根目录运行（需要 Python 3.10+、Git、Maven 和 JDK 21）：

```bash
python3 scripts/improvement_loop.py run --repo . --spec evals/foreground-cancellation.json --output /tmp/runner-cancellation-evidence
python3 scripts/improvement_loop.py verify --bundle /tmp/runner-cancellation-evidence --repo .
```

输出目录必须不存在且位于源仓库之外。run 先冻结源码与验收定义，再从副本运行 Maven；源工作区可以
继续编辑，但编辑后的内容需要重新 capture/evaluate。也可分两步执行：

```bash
python3 scripts/improvement_loop.py capture --repo . --spec evals/foreground-cancellation.json --output /tmp/runner-candidate
python3 scripts/improvement_loop.py evaluate --bundle /tmp/runner-candidate
```

原工作区改变后，使用保存的执行脚本重跑原候选，结果写入另一个新目录：

```bash
python3 /tmp/runner-candidate/snapshot/scripts/improvement_loop.py replay --bundle /tmp/runner-candidate --output /tmp/runner-candidate-replay
```

保存包中的脚本与评测版本一致；当前工具发生变化时，verify 也应使用该脚本读取历史包。

## 产物与判据

模型热切换样本使用同一个执行器，另有独立验收定义和两份 HTTP 请求证据：

```bash
python3 scripts/improvement_loop.py run --repo . --spec evals/execution-model-binding.json --output /tmp/runner-model-binding-evidence
python3 scripts/improvement_loop.py verify --bundle /tmp/runner-model-binding-evidence --repo .
```

该样本通过真实 Spring/MVC 切换配置，断言旧轮及其子任务、新并发轮、隐式接续实际请求中的模型。
`model-binding-sync.json` 和 `model-binding-reactive.json` 保存受控远端收到的 HTTP 载荷及工具实参，
不包含 API key；它证明 Provider 选择在执行期间稳定，完整配置清单与生产发布核对仍未接入。

| 产物 | 用途 |
|---|---|
| candidate.json / snapshot/ | 来源 HEAD、实际源码清单/摘要与独立执行副本，包含未提交和未跟踪代码 |
| evaluation.json | 冻结的验收定义及其内容版本，包含测试与执行脚本依据 |
| evidence/ | 用例导出的实际请求、执行参数、结果与生命周期事实 |
| report.json / build.log | 实际命令、Maven/JVM 环境、classpath JAR 与编译产物摘要、逐用例结果及必需证据 |
| handoff.md | 当前结论、原始反馈、失败原因、允许的改动范围及复现入口 |

capture 后 verify 返回 captured，只说明输入完整；执行通过才返回 passed，失败报告返回 failed。
通过需要构建成功、每个声明的测试类实际运行且全部通过、必需证据齐备并绑定同一候选/评测，
执行前后源码内容一致。verify 核对保存的证据，带 --repo 时还核对当前工作区。通过只覆盖声明的
受控场景，不是发布批准；任何源码、评测或报告内容变化都需要重新验证。

原始运行包可含测试输入和日志，不作为线上用户数据采集入口。忽略文件、ci-results 与本地
`.worktrees/` 容器不进入副本；
使用声明过的测试配置，勿依赖未记录的本地运行数据。首次构建仍可能下载 Maven 依赖。

## CI 结果与失败取证

CI 分别记录候选工具自检、冻结 native 候选和全仓构建三个阶段。`ci-results/latest.json` 必须绑定
触发提交 SHA、分支及 GitHub run ID，每个阶段只使用 `passed`、`failed` 或 `skipped`；冻结候选
同时记录候选/评测摘要，全仓构建记录本次退出码和测试摘要。

工作流在运行测试前初始化 `ci-results/`，所以即使前序阶段失败，`build.log` 也只能是本次构建输出
或带当前 SHA/run ID 的明确未运行说明，不能沿用仓库中的旧日志。完整候选包和 CI 摘要通过名含
run ID 与触发 SHA 的 artifact 在成功、失败时都上传；排障时先下载该 artifact，检查候选包内的
`report.json`、`handoff.md`、`build.log` 和 `evidence/`，不要只根据 job 的退出码推断测试原因。

本地可用以下命令验证阶段摘要的生成与 SHA 绑定；它不会调用模型或硬件：

```bash
python3 -B -m unittest discover -s scripts/tests -v
python3 -B scripts/ci_results.py --help
```

2026-10-18 本地复验：脚本自检 23 项通过；冻结 native 候选 23 项通过并产出两份绑定证据；
20 轮压力运行共 460 次用例执行无失败；全仓 clean test 为 2380 项、2358 通过、22 项环境条件跳过。
原 CI 39095202090 因旧工作流未上传候选包，无法恢复其内部失败详情；该事实必须与本地复验区分。

## 自检

候选比较（ADR-027）在完整核验两个已执行包后按相同评测摘要逐用例比较：

```bash
python3 scripts/improvement_loop.py compare --baseline /tmp/runner-before --candidate /tmp/runner-after --output /tmp/runner-comparison
```

新目录中的 `comparison.json` 保存候选、报告和评测摘要及逐用例前后状态；`handoff.md`
包含原始反馈、失败落点及两份证据目录。评测改变或删除用例不计作改善；修复一个用例但另一个
产生回归时结论为 `regressed`。`improved` 只表示相同的受控用例得到修复且候选评测整体通过。
两个包必须用同一版本的执行脚本实际运行；历史包的验证器版本不同，需要重新执行双方。
本命令输出比较结果，不执行发布；调用者应读取报告中的 status。

```bash
python3 -m unittest discover -s scripts/tests -v
mvn clean test
```

2026-09-06：15 项脚本测试通过；首个候选的 23 项 native 验收通过，产出 2 份运行证据。
其中旧子任务为 CANCELLED、执行器请求为空且模型收到拒绝结果；正常独立子任务实际传入
`{"request":"background"}`、模型收到 `moved background`，任务最终为 COMPLETED。
全量及后续状态统一记录到[实施进展](../design/runner-minion-system-evolution.md#124-2026-09-06-实施进展与续接点)。

ADR-022 模型绑定样本：2026-09-06 23:52:05 冻结副本 clean 验收 25 项通过，产出同步和 reactive
两份下游证据；候选包中的报告分别记录源码与评测摘要。变更本轮文档后重新 capture/evaluate，
不能把这个历史候选的通过结论直接用于编辑后的工作区。
