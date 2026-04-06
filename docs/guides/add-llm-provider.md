# Guide: Adding a New LLM Provider

## Steps

1. Create a new package: `com.lightweightai.kernel.llm.<provider>`
2. Implement `LLMProvider` interface (all four methods):
   - `complete()` — sync
   - `completeAsync()` — async
   - `completeStream()` — callback streaming
   - `completeStreamReactive()` — Reactor Flux streaming
3. Implement `ModelCapability` for the model
4. Wire into Spring configuration in `agent-web` or use builder pattern

## Reference
- Existing: `ClaudeProvider`, `ClaudeProProvider`, `OpenRouterProvider`
- Module: agent-kernel
- Package: `com.lightweightai.kernel.llm`
