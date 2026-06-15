# Minion 语音文本契约（大脑平面）

Voice Gateway ⟷ runner 的 WebSocket **文本**契约——ADR-006 D2 双平面里的「大脑平面」。
媒体平面（二进制音频，Device ⟷ Voice Gateway ⟷ 厂商 STT/TTS）**不**走此契约。

三仓共享：
- **runner**（本仓）：实现并以 `messages.schema.json` 校验出/入站帧（见
  `MinionVoiceTextSeamAcceptanceTest`）。
- **voice-gateway**（Python）：STT 文本按 `inbound.chat` 送入；VAD 触发 `inbound.interrupt`；
  消费 `outbound.speakable_chunk` 做流式 TTS、`outbound.speech_interrupted` 停播。
- **minion-body**（设备/硬件）：据 `outbound.speakable_chunk.emotion` 驱动表情、
  据 `outbound.speech_interrupted` 停播。

设计与字段语义见 [ADR-012](../../docs/decisions/012-voice-text-plane-contract.md)。

`messages.schema.json` 用 JSON Schema draft 2020-12，按消息类型分 `$defs`：
`inbound.chat`、`inbound.interrupt`、`outbound.token`、`outbound.speakable_chunk`、
`outbound.speech_interrupted`、`outbound.stream_end`。
