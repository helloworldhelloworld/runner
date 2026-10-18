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

## 增量轮次关联（ADR-015）

新双工客户端在 chat 上提供唯一 `turnId`，interrupt 携带目标 `turnId`。
runner 在可说块/打断/错误的顶层回传该标识，正常结束则放在 `meta.turnId`。
带关联的 `speech_interrupted` 本身就是该轮终态，不再等待 `stream_end`；后者只表示
生成完成，不表示喇叭播放完成。不带 `turnId` 的旧客户端保留原帧形状与隐式续接语义。
应先升级 runner，再升级双工 gateway；新客户端遇到未关联的旧服务响应会明确失败。

真实跨仓验收（SDK/麦克风不连云）：

```bash
mvn clean test -pl agent-web -am -Dtest=MinionVoiceTurnWebSocketTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dvoice.gateway.root=/absolute/path/to/voice-gateway \
  -Dvoice.gateway.python=/absolute/path/to/python
```

未指定 Python 仓路径时，仅跨仓子用例跳过；Java 对真实 Spring WebSocket 的用例仍执行。
该测试同时覆盖首声前打断、旧轮迟到打断、失败后继续下一轮，以及生成完成后的停播回执。
完整 runner 回归与实机验收边界见 [ADR-015 验证记录](../../docs/decisions/015-voice-turn-cancellation.md#runner-verification-recorded-on-2026-09-06)。
