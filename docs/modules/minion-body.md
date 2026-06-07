# Minion Body（树莓派瘦身体）

> 独立程序，**不在本 Java 仓内**，**Python**。Minion 的"身体"。
> 设计依据见 [ADR-006](../decisions/006-minion-embodiment-architecture.md)。

## Responsibility

树莓派端的瘦身体。**不跑 LLM、不跑 STT/TTS**（厂商 API 由 Voice Gateway 调）。
只负责唤醒、音频收发、表情/动作的实时同步（Realizer）、硬件驱动。无 JVM，负载轻。

## 职责清单

| 模块 | 干什么 | Python 选型参考 |
|---|---|---|
| 唤醒词 | 本地常驻监听 "Hey Minion" → 触发会话 | pvporcupine / openWakeWord |
| VAD/端点检测 | 判断一句话开始/结束（低延迟、省带宽、隐私）| webrtcvad / silero-vad |
| 音频上行 | 麦克风流 → Voice Gateway | sounddevice |
| 音频下行 | 播放 Gateway 下发的 TTS 音频 | sounddevice |
| **Realizer** | 按**音频播放时钟**对齐三轨 | 自研轻量状态机 |
| 眼睛(主表情) | 圆形 OLED 动画，emotion 驱动 | FluxGarage RoboEyes / Adafruit Uncanny Eyes（可下放 MCU）|
| 嘴 | viseme 时间标记 → 嘴形；MVP 用音频包络 | — |
| 手/头 | bookmark → 挥手/点头/转向 | gpiozero（舵机 PWM）|
| 摄像头 | `look` 工具触发，按需抓帧 → 降采样 base64 | picamera2 / rpicam-still |
| 动作工具 | 离散动作以 **MCP 工具**暴露给 runner | MCP Python SDK |

## 唤醒 → 对话链路（设备视角）

```
唤醒词命中 → 通知 runner 起会话(TriggerSource)
VAD 截到一句 → 音频上行 Gateway → (云)STT → runner
runner 流式可说块 → (云)TTS → 音频+viseme 下发 → 本地播放
   ⤷ Realizer 同步: 嘴(viseme) + 眼睛(emotion) + 手势(bookmark)
   ⤷ 用户插话 → VAD → barge-in 信号 → runner interrupt() → 停播
LLM 调 move/look 工具 → 经 MCP 回到 Body 执行
```

## Realizer（表情/语音对齐器）

ADR-006 D4 的"三轨 + 音频时钟"对齐器，**只在设备端**（口型同步必须紧贴音频时钟）：

- 嘴形轨：帧级，跟 viseme / 音频包络
- 情绪轨：句级，跟逐块 emotion → 眼睛形状 + LED 颜色 + 体态
- 手势轨：离散，跟 bookmark 音频偏移
- idle 基线：呼吸/眨眼/微动，无对话时维持"活着"的感觉

## 暴露给 runner 的工具（MCP, deviceType="minion"）

`move` / `turn_head` / `set_eyes` / `wave` / `nod` / `look`(capture_image) …

风险等级经 MCP 标准 `annotations` 声明（runner 据此恢复 `riskLevel()`，见
[ADR-007](../decisions/007-risk-level-across-mcp.md)）：物理动作 `destructiveHint=true`→`SYSTEM`，
`look` `readOnlyHint=true`→`SAFE`，`set_eyes`→`WRITE`。由 runner 侧 `ToolPolicy` 收口。

## Rules

- 不跑 LLM/STT/TTS；保持瘦
- 物理安全反射（**本期暂缓**，将来务必落本地，不走云）
- 与 runner 走 MCP（动作）+ device 协议经 Voice Gateway（唤醒/转写/播放/barge-in）；
  MCP 跨 cloud↔Pi 的 transport/NAT 穿透见 [ADR-008](../decisions/008-mcp-transport-cloud-to-pi.md)
