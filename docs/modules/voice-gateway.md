# Voice Gateway（语音媒体网关）

> 独立服务，**不在本 Java 仓内**、**非 JVM**。承载 Minion 的实时音频平面。
> 设计依据见 [ADR-006](../decisions/006-minion-embodiment-architecture.md)。

## Responsibility

把"原始音频"这条实时平面从 runner(大脑) 里彻底剥离。runner 只收发文本，
所有麦克风/喇叭音频字节、流式 STT/TTS、口型时间标记都在这里处理。

## Position（在系统里的位置）

```
媒体平面 (audio, 二进制, 绕开 JVM)        大脑平面 (text + 工具 + 事件)
Device ⟷ Voice Gateway ⟷ 厂商流式 STT/TTS        Voice Gateway ⟷ runner(JVM)
```

## 职责清单

1. **音频上行**：接设备麦克风流 → 厂商**流式 STT**（部分 + 最终转写）
2. **文本入脑**：最终转写文本 → 经现有文本协议送 runner（`GatewayRequest.message`）
3. **文本出脑**：runner 的 `TEXT_DELTA`/**可说块** → 喂厂商**流式 TTS**
4. **音频下行**：TTS 音频流 → 下发设备播放
5. **口型轨**：抓 TTS 厂商的 **viseme/speech-mark 时间标记**，与音频、bookmark 一起打包下发设备 Realizer
6. **桥接 barge-in**：设备插话信号 → 通知 runner `interrupt()` + flush 未合成的可说块

## 厂商 API 选型（ADR-006 D4）

- 流式 STT：Azure Speech 流式 / AWS Transcribe / Deepgram 等
- 流式 TTS：Azure Neural（`mstts:express-as` 情感 + `VisemeReceived`）/ AWS Polly（Speech Marks）/ ElevenLabs
- **口型免费**：选 Azure/Polly 即自带 viseme 时间标记，无需自做音频→音素分析（兜底 rhubarb-lip-sync）

## 与 runner 的契约（纯文本，不碰二进制）

| 方向 | 内容 |
|---|---|
| Gateway → runner | STT 最终转写文本（`GatewayRequest`，含 `sessionId`/`deviceContext`）；barge-in 中断信号 |
| runner → Gateway | `TEXT_DELTA` / 可说块（带逐块 emotion）；手势 bookmark；`stream_end` |

> runner 侧需补的"可说块边界事件 + 逐块 emotion"见 ADR-006 / architecture.md Embodiment 段。

## Rules

- **不得**把原始音频字节路由进 runner(JVM) 或其 WebSocket/StreamEvent 通道
- 语言不限，倾向非 JVM（贴近实时音频）；与 runner 间只走文本协议
- viseme/口型同步数据下发给**设备 Realizer**，Gateway 自身不做表情对齐
