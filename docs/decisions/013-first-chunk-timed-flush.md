# ADR-013：首块早发的时间维度兜底（max-wait timed flush）

状态：Accepted（2026-06-19）
关联：[ADR-005 StreamEvent 闭合协议](005-streamevent-closed-protocol.md)、[ADR-006 D2 大脑平面](006-minion-embodiment-architecture.md)、
延迟 spike 阶段 1（`docs/latency-spike-results.md`、`todo/2026-06-19.md` §2.1）。

## Context

首响延迟（用户说完 → 第一声音频，TTFA）的 runner 侧杠杆之一是 **chunk_form**：第一个
`SPEAKABLE_CHUNK` 多快成形（喂 TTS 的起点）。`SpeakableChunkProcessor.FirstChunkPolicy` 的
`eager` 已给首块两条**内容维度**的提前切边界：

- **子句末**（`clauseEnders`：逗号/顿号/冒号）——比整句更早；
- **字符上限**（`maxChars`，默认 24）——既无句末也无子句末时硬切。

缺口是**时间维度**：当 token 间隔被网络/模型拉长时，一段没有任何标点、又没到 `maxChars` 的文本会
把首块一直憋住——`maxChars` 只约束**字符数**，不约束**墙钟**，而 TTFA 预算是墙钟。
内容维度的两条边界都可能在慢 token 下久久不触发。

`SpeakableChunkProcessor.apply` 是 `Flux.defer → upstream.concatMap`：纯 concatMap 只能被**到来的事件**驱动，
silence（token 间隔）期间没有事件能触发"该发首块了"。要在静默中兜底，必须引入一个**独立于上游的时间信号**。

## Decision

给 `FirstChunkPolicy` 增加 **`maxWaitMillis`** 维度：eager 且 `maxWaitMillis > 0` 时，
**自首个 `TEXT_DELTA` 到达起计**，最多等 `maxWaitMillis` 毫秒——到点仍未发出首块（句末/子句末/`maxChars`
都没触发），就把当前缓冲区**整段** flush 为首个 `SPEAKABLE_CHUNK`。句末/子句末/字符上限/max-wait 四者**先到先发**；
首块之后回到纯句末切分，`maxWaitMillis` 不影响后续块。

### 实现（reactive，确定性可测）

- 仅当 eager 且 `maxWaitMillis > 0` 走 timed 分支；否则保持原 `concatMap` 路径，**完全向后兼容**。
- timed 分支把上游事件流与一条 **timeout 信号流** `Flux.merge`，合流后 `concatMap` 串行处理
  （`merge` 保证 onNext 串行 → 共享 `buffer` 访问安全，无需 `publishOn`）：
  - 定时器**自首个 `TEXT_DELTA` 起计**：`Sinks.One` 在第一个文本 delta 处触发，
    `flatMapMany(→ Mono.delay(maxWaitMillis))` 发出一个**内部哨兵**（私有 `StreamEvent` 实例，
    仅用 `==` 身份比较，**绝不下发**，故不引入新 `EventType`，不违反 [ADR-005](decisions/005-streamevent-closed-protocol.md)）。
  - 哨兵被消费时若 `index == 0`（首块仍未发）→ 整段 flush 缓冲为首块。
  - 首块一旦由任何路径发出，或上游终止 → `takeUntilOther` 取消待发的定时器，
    **不给流完成增加任何延迟**（短答经 `LLM_COMPLETE` flush 即取消定时器）。
- 测试用 **reactor `VirtualTimeScheduler`**（`StepVerifier.withVirtualTime`）驱动虚拟时钟，
  **零真实 `sleep`**：dribble 一段无边界文本 → `expectNoEvent(maxWait-ε)` → 跨过 `maxWait` → 断言首块发出。

### 默认值与接线

- 既有工厂 `sentenceOnly()` / `eager(clauseEnders, maxChars)` / `eagerDefault()` 一律 `maxWaitMillis = 0`
  （**不变**，`LatencySpikeBench` 的 default-vs-eager 对比保持纯净，只归因到内容维度）。
- 新增 `eager(clauseEnders, maxChars, maxWaitMillis)` 显式开启时间维度。
- 是否把 max-wait 接进生产（语音 persona 默认）连同 ADR-013 一并由 bench 数据定（todo §2.1-2/3）。

## Consequences

- ✅ 慢 token / 长间隔下首块有墙钟上限，直接约束 TTFA 预算占用，补齐内容维度的盲区。
- ✅ 纯增量：默认行为与所有既有工厂/测试不变；仅显式传 `maxWaitMillis>0` 才启用。
- ✅ 哨兵不下发、不入对话历史/下一轮（与 `SpeakableChunkProcessor` 既有"切块只作用于输出"边界一致）。
- ⚠️ timed 分支较 `concatMap` 复杂（merge + 定时器 + 取消）。靠虚拟时钟确定性测试 + `takeUntilOther`
  保证"首块发出/上游终止即取消定时器"，避免给流完成挂尾延迟。
- ⚠️ 到点整段 flush 可能把一个词切碎——首响优先，宁可切碎（与 `maxChars` 硬切同理；且只影响 TTS 首句韵律，
  不影响模型理解）。

## Alternatives considered

1. **从订阅起计 max-wait**（而非首个 delta）——更简单（一条 `Mono.delay`），但定时器会在 `LLM_TTFT` 期间
   空烧：text 还没来就到点，flush 空缓冲（no-op）并耗掉唯一的兜底，恰好错过"text 来了但 dribble"这一真实场景。否决。
2. **不做，靠 `maxChars` 兜底**——`maxChars` 约束字符不约束墙钟，慢 token 下仍憋首块。这正是本 ADR 要补的盲区。
3. **`bufferTimeout` 算子**——只能整流统一超时，无法只对**首块**施加、且不与句末/子句末/字符上限取最早。否决。
