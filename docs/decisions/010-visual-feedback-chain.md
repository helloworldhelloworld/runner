# ADR-010：视觉回灌链（tool frame → ImageContent → 下一轮多模态消息）

状态：Accepted（2026-06-15）
关联：[ADR-006 D6 视觉=按需抓帧的多模态](006-minion-embodiment-architecture.md)、[ADR-002 Tool over Plugin](002-tool-over-plugin.md)

## Context

ADR-006 D6 定义了小黄人的"看"：LLM 调 `look`/`capture_image` → Pi 抓一帧 →
降采样 base64 → **经现有 `ImageContent` 进多模态消息**。当时把缺口判断为
"ClaudeProvider/OpenRouter 真正把 image block 写进请求体（接口已有，实现缺）"。

provider 序列化这一段已补齐（`ClaudeProvider.buildContentBlocks` /
`OpenRouterProvider.buildContentBlocks` 真正把 `ImageContent` 写进出站 body，
并有 `*ProviderMultimodalTest` 的 payload-capture 测试背书）。

但**回灌链本身从未接通**，存在两处断点：

1. **`ToolResult` 没有图像通道** —— 它只携带 `String content`（+ 可选
   `structuredContent` Map）。`look` 抓到的帧无处可放，工具只能返回文本。
2. **`ToolCallingLoop.createToolResultMessage` 只发文本** —— 即便结果带了帧，
   回灌成下一轮 `ConversationMessage` 时也只 `textContent(result.getContent())`，
   图像被丢弃。

后果：D6 的"看"在生产代码里是断的；M0 验收测试曾用纯文本字符串
`"[image:minion-sees-a-cat]"` 冒充图像、以 `ToolResult` 文本回流冒充视觉回灌，
掩盖了这条链不存在的事实（见巡检 R-1）。

## Decision

打通 **tool frame → `ImageContent` → 下一轮多模态 `ConversationMessage` → provider body 的 image block**，且**复用已验证的 provider 序列化路径，不改 provider**。

### D1. `ToolResult` 增加图像附件通道（additive-only）

`ToolResult` 新增 `List<ImageContent> images`（默认空），并提供：

- 工厂 `ToolResult.withImages(String content, List<ImageContent> images)`
  与便捷 `ToolResult.image(String content, ImageContent image)`；
- getter `getImages()`（永不返回 null）、`hasImages()`；
- `withToolUseId(...)` **必须保留** images（executor 在
  `tool.execute(args).withToolUseId(id)` 处绑定 id，附件不能在此丢失）。

全部为新增成员，不改任何既有签名、不删任何成员 —— 满足 CLAUDE.md
"Public API Stability"（`Tool.execute` 返回 `ToolResult`，其表面属对外契约）。

### D2. 回灌：帧另起一条 USER 消息，而非塞进 TOOL 消息

`ToolCallingLoop` 在把工具结果加入对话时：先照常加 `MessageRole.TOOL` 文本消息
（保持既有 tool_result 线格式不变），**若结果带 images，再追加一条
`MessageRole.USER` 消息**，其 content 为 `[TextContent(caption), ImageContent...]`。

**为什么另起 USER 消息、而不是把 image 塞进 TOOL 消息：**

- Claude：`MessageRole.TOOL → "user"`，含非文本块即走 `buildContentBlocks`，
  塞进 TOOL 消息本可行。
- OpenRouter/OpenAI：`role:"tool"` 消息在 `buildMessagesArray` 走**专门的
  tool 分支**（只发 `getTextContent()` + `tool_call_id`），且 OpenAI Chat API
  **本就不允许 image 出现在 tool 角色消息里**。塞进 TOOL 消息会被静默丢弃。

→ 追加一条独立 USER 消息，是**唯一对两个 provider 都正确**的单一代码路径，
也是业界把"工具返回的图"喂给多模态模型的标准做法。它命中两个 provider 都已
验证的 `hasNonTextContent → buildContentBlocks` 序列化，因此**无需改 provider**。

> 连续 user 消息：本仓 `ToolCallingLoop` 在多个工具结果时本就会产生连续
> `TOOL(→user)` 消息，追加一条 USER 帧消息与既有行为一致。

### D3. 端到端 payload-capture 验收测试（先红后绿）

按 CLAUDE.md UT 规则 3/5，新增 `MinionVisualFeedbackChainAcceptanceTest`：
`look` 工具返回真实 `ImageContent` 帧 → 跑 `ToolCallingLoop` →
(a) 断言**下一轮对话**里出现携带该帧字节的 `ImageContent` 块；
(b) 把该轮对话喂给**真实** `ClaudeProvider`/`OpenRouterProvider`（`CapturingHttpClient`），
断言出站 HTTP body 含 image block。全链路真，不再用文本字符串冒充图像。

同时升级 `MinionLoopM0AcceptanceTest`：`look` 返回真实帧，round-2 断言帧已回灌。

## Consequences

- D6 的"看"在 runner 侧真正闭环：抓帧能进下一轮 LLM 请求体。
- provider 层零改动，风险面只在 `ToolResult`（additive）与 `ToolCallingLoop`（内部）。
- 端侧/MCP 的 `look` 工具只需在 `ToolResult` 里附 `ImageContent`（URL 或 base64 字节），
  回灌与序列化由内核负责。
- 暂仅支持 image；audio/video 块沿用 `ContentBlock` 既有"未识别即跳过"策略，按需再扩。
