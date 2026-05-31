# ADR-006: Minion 具身化架构（Embodiment）

## Status
Proposed（大架构选型已定，实现未开始）

## Context

要把 runner 落成一个端到端的实体小黄人：3D 打印外壳 + 树莓派 + 麦克风 + 摄像头 + 舵机/LED，
能力为**唤醒 → 语音对话 → 视觉 → 动作/表情**。第一优先级是**对话自然流畅 + 音色好听**。

调查现有底座（见 architecture.md）后确认 runner 已具备约 70% 概念骨架可直接复用：

- `TriggerSource` SPI —— 唤醒/主动行为
- `InterruptibleRun.interrupt()/resume()` —— 对话打断（barge-in）
- `ContentBlock` 已定义 `IMAGE`（`ImageContent` 支持 URL/base64）—— 视觉输入
- `DeviceContext + DeviceToolBinding + DispatchingTool` —— 按设备类型分发工具
- MCP 流式工具（`McpToolSourceProvider`）—— 离散动作工具
- `SpeechProvider`（OpenAI/Azure STT+TTS，含 emotion）—— 语音
- `StreamEvent` 流里 `emotion` 已通到 `stream_end`

缺口集中在：实时音频流水线、多模态实际接线（ClaudeProvider 当前只发 `getTextContent()`）、
表情/语音同步、Minion 人格运行时。

## Decision

### D1. 拓扑：脑在云（brain-in-cloud）

runner（JVM）跑在云/服务器，树莓派只做**瘦身体（thin body）**。
runner 与 LLM/语音服务同侧，多步工具循环和视觉重提示在服务端打转，不必每步 Pi↔云往返。

### D2. 双平面分离：媒体平面 ⟂ 大脑平面

**原始音频永远不进 runner(JVM)。** 系统拆成两条独立平面：

```
媒体平面 (audio): Device ⟷ Voice Gateway ⟷ 厂商流式 STT/TTS   —— 二进制音频，绕开 JVM
大脑平面 (text) : Voice Gateway ⟷ runner                      —— 纯文本 + 工具 + 事件
```

耦合点只有文本：STT 转写文本 → runner 输入；runner `TEXT_DELTA`（可说块）→ TTS 输入。
**因此放弃"二进制媒体走 runner WebSocket"的方案**——runner 的传输层保持纯文本，不新增二进制帧。

### D3. 新增组件：Voice Gateway（独立服务，非 JVM）

承载所有实时音频活，**不放进 runner**：

- 厂商**流式 STT**（部分 + 最终转写）
- 厂商**流式 TTS**（高音质神经音色，边收文本边下发音频）
- 从 TTS 厂商抓 **viseme/speech-mark 时间标记**，连同音频与 bookmark 一起下发设备
- 桥接：STT 文本 → runner；runner 可说块 → TTS

语言不限（Go/Rust/Node/Python 皆可，倾向非 JVM 以贴近实时音频）。

### D4. 语音/口型/表情：厂商流式 API + "三轨 + 音频时钟 Realizer"

STT/TTS **用厂商一体化流式 API**（如 Azure Speech / AWS Polly），不自建模型。

表情与语音的对应采用 ECA 领域成熟的 **SAIBA/BML 思想的轻量版**——输出拆成三条并行轨，
在**设备端**用统一的**音频播放时钟**对齐（对齐器 = Realizer）：

| 轨 | 粒度 | 驱动信号 | 部位 |
|---|---|---|---|
| 嘴形 (lip-sync) | 帧级 | TTS 的 **viseme 时间标记**（Azure VisemeReceived / Polly Speech Marks）；MVP 退化为音频包络 | 嘴 |
| 情绪/神态 | 句级 | LLM 流的**逐块 emotion 标签** | **眼睛(主表情)** + LED + 体态 |
| 手势/动作 | 离散点 | 文本内 **bookmark 标记**（SSML `<bookmark>`）回报的音频偏移 | 手/头 |

**Realizer 必须放设备端**（口型同步要紧贴音频时钟，不能走云往返）。
眼睛为小黄人主表情通道（圆形 OLED + 动画库如 FluxGarage RoboEyes）。

### D5. 树莓派 = 瘦身体，用 Python

Pi 不跑 LLM、不跑 STT/TTS（厂商 API 由 Gateway 调）。Pi 只负责：

- 本地**唤醒词 + VAD/端点检测**（低延迟、省带宽、隐私）
- 麦克风音频上行流 / TTS 音频下行播放
- **Realizer**：按音频时钟驱动 嘴(viseme/包络) + 眼睛(emotion) + 手势(marks) + idle 基线
- 舵机/LED/摄像头驱动；**离散动作以 MCP 工具暴露**给 runner（`deviceType="minion"`）

无 JVM、负载轻，**Python 即可**（生态最全：picamera2 / sounddevice / pvporcupine / gpiozero）。

### D6. 视觉 = 按需抓帧的多模态

不做连续视频流。LLM 调 `look`/`capture_image` 工具 → Pi 抓一帧 → 降采样 base64 →
经现有 `ImageContent` 进多模态消息。**需补：ClaudeProvider/OpenRouter 真正把 image block 写进请求体**（接口已有，实现缺）。

### D7. 暂缓项

- **物理安全**（急停/边缘/障碍反射）**本期不做**；将来一定**落在设备端**，绝不走云往返。
- 二进制媒体走 runner（#11）—— 不做（被 D2 取代）。
- 重视觉（持续目标跟踪/本地人脸识别）—— 不做。

## runner 侧实际改动（最小）

实时音频引擎都在 Gateway/设备，runner 只补让对话流畅的几点：

1. **可说块边界事件** —— LLM 流式输出按小句切"可朗读块"，让 Gateway 第一句没说完就开始合成下一句（边想边说，首响快）
2. **逐块 emotion/韵律** —— 把 `emotion` 从 `stream_end` 提前到每个可说块（眼睛/神态的驱动源）
3. **Barge-in 接线** —— 设备/Gateway 的插话信号 → `InterruptibleRun.interrupt()` + flush 未说出的块
4. **多模态接线** —— D6
5. 运动/表情**工具集** —— MCP，`DeviceToolBinding(deviceType="minion")`

> 若新增 `StreamEvent` 类型（如 `SPEAKABLE_CHUNK` / `SPEECH_INTERRUPTED`），
> 必须按 [ADR-005](005-streamevent-closed-protocol.md) 闭合枚举规矩加 EventType + 工厂方法，禁止字符串约定。

### Public API 稳定性约束（硬约束）

本期及未来的具身化改动**不得破坏** `Tool` / `ToolSourceProvider` / `agent-mcp` 对外类型——
这些是其他项目依赖的契约（详见 CLAUDE.md "Public API Stability"）。具体到本架构：

- 运动/表情工具是**新写的 `Tool` 实现**；新增能力走 `default` 方法 / 新类型 / 新枚举值，不改既有签名。
- MCP 在此架构里是被 **消费**（runner 连 Pi 的 MCP server），runner 不改 MCP 接口定义。
- 多模态接线只动 `ClaudeProvider`/`OpenRouter` 内部，复用既有 `ContentBlock`/`ImageContent`。
- 禁止以"清理"名义删除 public / `@Deprecated` 成员。

## 延迟预算（"自然流畅"的量化目标）

目标：用户说完 → 小黄人开口 **< ~1s**，靠全程流式 + 流水线：

```
本地VAD端点  ~250ms
流式STT最终  ~150ms   (边说边转)
LLM 首token  ~400ms   (选快模型)
凑出第一句   ~150ms → 触发TTS，不等整段
TTS 首段音频 ~250ms   (流式)
网络往返     ~2×50ms
─────────────────────
首次出声 ≈ 0.9–1.3s，之后逐句流式跟上
```

## Consequences

- **Positive**: runner 保持纯文本 + 工具契约，核心几乎不动；音频/口型复杂度全隔离在 Gateway+设备
- **Positive**: 厂商流式 TTS 白送 viseme，口型同步几乎零成本；音色由厂商保证
- **Positive**: Pi 瘦、Python 上手快；脑在云便于集中升级、跑大模型
- **Trade-off**: 强依赖网络（断网即哑，仅 idle 微动作可本地降级）
- **Trade-off**: 新增 Voice Gateway 一个独立服务与运维面
- **Trade-off**: 厂商流式 API 锁定（STT/TTS 绑定 Azure/Polly 等）
- **Deferred**: 物理安全、二进制媒体传输、重视觉
