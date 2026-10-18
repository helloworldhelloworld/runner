# ADR-015: Correlated voice turns and cancellation

Status: Accepted (2026-09-06). Runner implementation and automated verification
complete in the working tree; Pi/device validation remains pending.

## Decision

This increment closes the existing Spring `/ws/chat` ↔ Python duplex path. It does
not introduce another service, change public Tool/MCP APIs, or implement hardware
motion, playback-clock realization, or persistent conversation history.

- Voice clients opt in by supplying a unique `turnId` on `chat` and the target
  `turnId` on `interrupt`. `sessionId` survives turns; `runId` remains a brain
  execution identifier. A late interrupt must never cancel a newer turn.
- Echo `turnId` on `speakable_chunk`, `speech_interrupted`, and `error`; put it in
  `stream_end.meta.turnId`. These locations preserve existing strict token/end
  envelopes and existing clients. Requests without `turnId` keep legacy shapes.
  Correlated clients reject uncorrelated speech/terminal frames with an explicit
  upgrade error; they must not silently guess ownership on an old server.
- `speech_interrupted` terminates that response, without requiring `stream_end`.
  Normal `stream_end` means generation ended, not that hardware finished playing.
  Reuse the existing typed `SPEECH_INTERRUPTED`/`ERROR` events, not TRACE strings.
- Reserve a connection's turn before asynchronous chat work starts. Cancellation
  also applies before subscription; cleanup uses identity, so an old callback
  cannot remove the next turn. Only the targeted turn may be interrupted.
- The Pi cancels locally before waiting for the network: wake pending consumers,
  abort playback, stop synthesis, discard old messages, and retain the ongoing
  input. Audio subscription closure must not depend on a free queue slot.
- An STT sentence-final is not utterance completion. Accumulate final segments to
  the endpoint, and hand the entire barge-in utterance to the next response once.
  Keep the same capture subscription when speech transitions into barge-in input.
- Natural drain, cancellation/abort, and failure are distinct. Local cancellation
  does not undo a completed tool side effect. Remote reconnect/replay, phoneme
  alignment, and precise played-text history remain subsequent increments.
- Native `InterruptibleRun` completes its outer stream on explicit interruption,
  cancels the execution token on downstream disposal, and guards callbacks by
  execution identity. AgentLoop alone commits the resumed user input to memory.
  Failed executions leave RUNNING; old cleanup cannot evict a replacement run.
- Orchestrator routing and run allocation begin on subscription. A cancellation
  while delivering Gateway's user trace or the route/interrupt/resume prefix must
  not reserve a RUNNING execution that has no subscriber. Prefix-only cancellation
  releases a fresh run; a resumed run retains only its interrupted context.

## Verification

Outside-in tests cover A interrupted before first audio, complete multi-segment B
submitted once, A's late chunks/end rejected, cancellation without stream_end,
full audio queues, and failures. Exercise real Spring WebSocket handling and the
real Python WebSocket transport; fake only controlled peers/devices. No sleep or
connection-ready polling may hide production races.

Kernel acceptance tests also cancel at the Gateway trace and Orchestrator route
boundaries, then inspect the next LLM request and lifecycle events. This proves
that a request cancelled before AgentLoop starts neither enters history nor
causes a phantom interruption on the next turn.

### Runner verification recorded on 2026-09-06

| Test | Verified boundary |
| --- | --- |
| `VoiceTurnCancellationAcceptanceTest` | Unsubscribed requests and cancellation at trace/route prefixes do not create phantom interrupt/resume events; the next LLM receives B exactly once. |
| `InterruptibleRunTest` | Explicit interruption closes the outer stream; downstream disposal saves partial context; failed runs can restart. |
| `MinionVoiceTextSeamAcceptanceTest` | Legacy frame shapes and correlated speakable/end frames. |
| `MinionVoiceTurnWebSocketTest` | Real Spring WebSocket cancellation, late A interruption during B, failure recovery, cancellation after generation, and the actual Python transport round trip. |

The three prefix-cancellation cases failed before the Orchestrator fix because B
received A's phantom `AGENT_INTERRUPT`, `SPEECH_INTERRUPTED`, and `AGENT_RESUME`.
They passed after routing/allocation and execution were deferred to subscription.

Full reactor verification passed with the optional Python probe enabled:

```bash
mvn clean test \
  -Dvoice.gateway.root=/absolute/path/to/voice-gateway \
  -Dvoice.gateway.python=/absolute/path/to/python
```

All 13 modules built successfully. The cross-repository test uses the real Python
WebSocket transport against local Spring/Tomcat and a controlled LLM peer. It does
not validate cloud STT/TTS, acoustic barge-in, or actual Pi playback; those require
the paired device deployment and a separate acceptance record.
