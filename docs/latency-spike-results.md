# 语音首响延迟 Spike — 结果（阶段 1：runner 微基准）

> 方法与依据见审批方案；北极星 **TTFA P50 < 1.3s**（用户说完 → 第一声音频）。
> 本阶段只量 runner 侧、方差主导的两段：**LLM_TTFT** 与 **chunk_form**（不含 EOU/STT/TTS，那些在全链 harness 阶段 2-4）。

## 怎么跑

```bash
LATENCY_SPIKE=1 \
OPENROUTER_API_KEY=sk-... \
OPENROUTER_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
OPENROUTER_MODEL=qwen-plus \
LATENCY_SPIKE_REPS=30 \
mvn -pl agent-kernel test -Dtest=LatencySpikeBench
```

bench：`agent-kernel/src/test/java/com/lightweightai/kernel/bench/LatencySpikeBench.java`。
默认 `mvn clean test` **不会跑它**（类名 `*Bench` 不匹配 surefire 模式 + `assumeTrue` 双重门控），不污染 CI。

## 分段计时口径（都从订阅起算，单调时钟）

- **LLM_TTFT** = 订阅 → 第一个 `TEXT_DELTA`（time-to-first-token）。
- **chunk_form** = 订阅 → 第一个 `SPEAKABLE_CHUNK`（首个可说块成形，喂 TTS 的起点）。
- 生产侧同口径标记：`TracingPostProcessor` 发 `llm.first_token` / `speakable.first_chunk` TRACE（带 `latencyMs`），trace viewer 可见。

## 延迟预算对照（业界线）

| 段 | 业界目标 | 本次实测 P50 | P95 |
|---|---|---|---|
| LLM TTFT | < 400ms | _TBD_ | _TBD_ |
| chunk_form（默认句末） | —（越小越好） | _TBD_ | _TBD_ |
| chunk_form（eager 早发） | —— | _TBD_ | _TBD_ |
| _（EOU / STT / TTS_TTFB —— 阶段 2-4）_ | STT<200ms · TTS_TTFB<150ms | —— | —— |

> 业界基线参考（LiveKit/HF playbook）：简单轮 TTFA ~1.39s P50 / 3.38s P95；LLM TTFT ~566ms P50 / 2.2s P95（方差主导）；TTS TTFB ~243ms；EOU ~554ms。

## 结果（运行后填）

```
（粘贴 bench stdout：默认 vs eager 的 LLM_TTFT / chunk_form 的 P50/P95/P99）
```

## 杠杆增益（运行后填）

- **首块 eager 早发**（子句/24 字符）：chunk_form P50 _默认 → eager_，降 _N_ ms。
- **首块 max-wait 时间兜底**（[ADR-013](decisions/013-first-chunk-timed-flush.md)，`FirstChunkPolicy.eager(clause,maxChars,maxWaitMillis)`）：
  慢 token / 长间隔下给首块墙钟上限；bench 默认未开（`maxWaitMillis=0`，保对比纯净），是否接生产看本表 chunk_form 是否主导。
- 判断：首响主导段是 _（LLM_TTFT / chunk_form）_；要达 TTFA<1.3s，需 _（裁上下文/小模型/co-location / 全链 harness 验 STT+TTS）_。

## 结论与下一步（运行后填）

- runner 两段合计 P50 ≈ _TBD_，对 TTFA<1.3s 预算的占用 ≈ _TBD_。
- 若 eager 显著降 chunk_form：考虑把 `FirstChunkPolicy.eagerDefault()` 设为语音 persona 的默认（接线在 `agent-web` 的 speakable Bean，按部署/persona 开关）——届时升 ADR。
- 推进全链 harness（阶段 2-4，进 voice-gateway）量完整 TTFA。
