# Minion 四仓系统联合审视与目标设计

整理日期：2026-09-06。

后续系统梳理见[整体架构文档](../system-architecture.md)，其中重新核对当前工作区、
取消修复后的验证结果、目标职责与演进依赖。本文继续保留原始 review 时点的发现。

本文补存用户所指的“昨天那次分析总结”：先审视 runner，再联合
voice-gateway、minion-body、minion-eyes，并结合
[/Users/hello/Documents/小黄人/files](/Users/hello/Documents/小黄人/files)
中的目标、硬件决策、采购和装配设计进行分析。

原始会话跨午夜；以下三份结论的本地时间分别是 2026-09-06
00:55、01:19、01:49（Asia/Shanghai）。当时完整分析只保留在对话中；
后续 [ADR-015](../decisions/015-voice-turn-cancellation.md) 仅提取了第一阶段的实施决策，
不能代替这份整体审视。

## 阅读说明与状态

- 以下正文保留当时三轮分析的完整结论，区分“发现的问题”和“建议的目标设计”。
- 文中的“当前”“已复现”、代码行号和测试数量均指**原始 review 时点**，
  不是对本次工作区的重新审计。随后已开始修复部分轮次与取消问题，
  因而不能把所有历史问题都当作今天仍未修复。
- 原文提及的临时探针位于 `/tmp`，可能已清理；此处保留其历史引用，
  不将它们冒充为已纳入仓库的回归测试。
- 本文是审视记录与目标方案。已接受的具体决策以对应 ADR 为准；
  硬件、Pi 实机、声学表现和固件结果不因文档落盘而视作已验证。

## 结论索引

总体判断：保留四仓分工、向下依赖、Tool/MCP 和文本／音频分离。
优先把一次对话的身份、输入、生成、播放、取消和执行结果贯通，
使系统形成可靠的桌面陪伴交互闭环。

| 范围 | 审视结论 | 建议方向 |
|---|---|---|
| runner 运行时 | 会话工具作用域、执行鉴权、设备上下文、历史、Skill 和预算存在接线缺口 | 明确全局配置、会话数据和执行状态的归属，贯通真实下游载荷 |
| 四仓部署 | 实际入口是远端大脑＋Pi 本地语音与身体组合，四仓不等于四个服务 | 先支持一个明确的 Pi 运行入口 |
| 输入与取消 | STT 句级 final、用户发言结束、生成结束、播放结束混用 | 分别管理输入、生成、播放状态；完整保留插话 |
| 跨仓协议 | 消息形状已有 schema，轮次归属与终态仍缺统一约束 | 区分 sessionId、turnId、runId、语句序号；本地取消不等网络确认 |
| 声音与表情 | 首响口径不完整，Realizer 未接主链，设备能力可能虚报成功 | 以播放端时钟驱动执行，报告真实能力与失败 |
| 硬件方案 | 麦克风、舵机、AEC 输出路径及装配图存在版本分歧 | 统一现行硬件方案后重新验算和实测 |

推进顺序：

1. 闭合语音轮次与打断：完整 B 只提交一次，旧 A 不串入 B，所有等待可退出。
2. 边采边转写，统一从用户末个语音采样到实际播放的延迟测量。
3. 把眼睛和逐句情绪接入真实播放链，协调 MCP 与 Realizer。
4. 统一会话、持久化和播放记录，区分已生成、已播放、部分播出。
5. 接真实总线舵机，并联合验证装配、声学、电源、热余量和本地保护。

## 第一轮：runner 代码设计审视

我的判断：runner 的模块分层和扩展接口基本合理，但主执行链还没有统一会话状态、权限和生命周期的管理。当前最需要的是把这些跨组件链路接完整。

值得保留的是单向模块依赖、`Tool` 扩展体系、`ChatHandler` 大脑适配边界，以及统一的 `Flux<StreamEvent>` 协议。主要问题按优先级如下：

1. **P1：会话专属工具污染全局注册表。**
   `Orchestrator` 创建携带 `parentSessionKey` 的 `SpawnSubagentTool`，但 `ScopedToolRegistry.register()` 实际写入全局 parent。我已复现：B 会话注册后，A 会话查到的 spawn 工具使用 B 的 session key。另一个顺序问题是工具定义在注册前已经快照，首次请求看不到 spawn 工具。应采用会话局部工具表，或通过执行上下文传递会话信息。见 [Orchestrator.java:207](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/orchestrator/Orchestrator.java:207)、[ScopedToolRegistry.java:92](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/agent/ScopedToolRegistry.java:92)。

2. **P1：权限检查尚未覆盖真正的执行条件。**
   策略求值始终传入 `args=null`，执行器没有用实际参数重新鉴权。我已复现：配置“执行参数已知时 DENY”，工具仍执行成功。此外，`SpawnSubagentTool` 没覆写风险等级，实际为 `SAFE`。工具可见性过滤与执行授权需要分别落实。见 [ScopedToolRegistry.java:96](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/agent/ScopedToolRegistry.java:96)、[SpawnSubagentTool.java:17](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/orchestrator/SpawnSubagentTool.java:17)。

3. **P1：打断状态机没有完整处理流的终止。**
   `interrupt()` 取消内部订阅，却没有终止外层流；`resume()` 与 `AgentLoop` 又各写一次用户消息。独立诊断确认：接续消息出现两份，旧流仍未终止。错误和下游取消也没有对应的状态收尾。建议明确每个 run 的终态，并让消息写入只有一个负责人。见 [InterruptibleRun.java:115](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/orchestrator/InterruptibleRun.java:115)。

4. **P1：设备上下文在编排链中丢失。**
   `GatewayRequest` 支持 `DeviceContext`，但 `Orchestrator → AgentLoop → ToolCallingLoop` 没把它传进工具执行上下文。我已复现：请求指定 `phone`，实际执行默认工具实现，phone 实现调用次数为零。多设备分发目前不能仅靠底层分发器的单测保证。见 [AgentLoop.java:242](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/agent/AgentLoop.java:242)、[ToolExecutor.java:269](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/core/ToolExecutor.java:269)。

5. **P1：聊天与历史管理使用了不同的数据路径。**
   native 聊天使用带 agent 前缀的 session key，写入 `FileMemoryAdapter` 的内存历史；历史查询仍由旧 `SoulComfortChatService` 按原始 sessionId 读取磁盘转录，清理也走旧标识。因此查询、清空和重启恢复无法与当前聊天保持一致。另外，长期记忆与检索共享存储、没有用户作用域，多用户部署时需要明确隔离。见 [FileMemoryAdapter.java:43](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/config/FileMemoryAdapter.java:43)、[SoulComfortChatService.java:333](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/service/SoulComfortChatService.java:333)。

6. **P2：Skill 发布没有接入当前 native 聊天路径。**
   发布服务向共享 `PromptEngine` 注册 Skill；`AgentLoop` 自建另一个 engine，而且构建请求时手工拼 prompt，没有调用 `PromptEngine.build()`。所以发布成功并不意味着新编排路径会使用该 Skill 的提示和触发规则。见 [SkillPublishService.java:164](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/skillcreator/SkillPublishService.java:164)、[AgentLoop.java:304](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/agent/AgentLoop.java:304)。

7. **P2：预算与上下文保护尚未完整接线。**
   `maxBudgetTokens` 没有接到 tracker 创建，实际响应 usage 也没有被记录。诊断中预算设为 10、每轮返回 200 tokens，仍继续第二轮，累计记录为零。压缩也只发生在进入工具循环之前，覆盖不到循环中不断增加的工具结果。见 [ToolCallingLoop.java:248](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/core/ToolCallingLoop.java:248)。

这些问题指向同一个设计改进：明确“全局配置、会话数据、单次执行状态”的归属，用统一执行上下文贯穿身份、设备、权限、取消和预算；让聊天、历史管理、Skill 发布消费同一套运行时服务。完成后再拆分过大的 Web handler、收敛 SDK 与业务 Agent 的重复执行路径，会更稳妥。

本轮没有修改仓库代码。`mvn clean test` 已通过：共 2216 项，20 项跳过，零失败、零错误；上述多个问题通过仓库外的独立诊断复现，说明后续测试应重点补齐多会话交错、真实参数传递和取消后的完整行为。


## 第二轮：四仓全貌、实体设计与系统问题

整体看下来，我的判断是：**四个工程的分工方向合理，但目前更像“有真实语音、真实眼睛适配的系统原型”，还没有形成完整可靠的具身对话闭环。最需要补齐的不是更多功能，而是跨工程的对话轮次、取消、播放时钟和设备能力契约。**

以下基于当前工作区代码，以及“小黄人/files”中的决策、采购清单、阶段规划和三版设计图。没有修改工程代码；实际 Pi 部署及硬件表现尚未现场验证。

### 一、先看系统全貌

小黄人的目标是个人桌面陪伴机器人：对话自然、眼睛有表现力、动作安静、声音好听。它不是首先要成为一个通用机器人平台。这个定位应该决定技术优先级。[项目目标](/Users/hello/Documents/小黄人/files/README.md:26)

按当前主入口代码，还原出的系统是：

```text
                         云端模型服务
                   ┌─────────┴─────────┐
                 STT / TTS             LLM
                   ↕ 音频               ↕
┌──────────── Pi 侧 ────────────┐      runner
│                              │   人格 / 会话 / 记忆
│ 麦克风 → voice-gateway ───────┼── 文本、可说块、打断
│             ↓                │         │
│        ALSA / aplay → 喇叭   │         │ MCP 工具调用
│                              │         ↓
│          minion-body ←───────┼─────────┘
│             │                │
└─────────────┼────────────────┘
              ├─ USB → 左眼 ESP32：minion-eyes
              ├─ USB → 右眼 ESP32：minion-eyes
              └─ 舵机 / 摄像头：当前仍有模拟实现
```

这里有一个重要区别：**四个仓库不等于四个独立服务。**

ADR-006 设想了独立 Voice Gateway 服务，但当前 `gateway_voice_duplex.py` 直接使用 `minion-body` 的采音、VAD 等组件，同时负责本地播放；没有在这条入口中形成独立的“Pi ↔ 远端语音网关”媒体传输层。当前更接近“远端大脑＋Pi 本地语音运行时”。[设计拓扑](/Users/hello/Documents/sourcecode/runner/docs/decisions/006-minion-embodiment-architecture.md)、[实际入口](/Users/hello/Documents/sourcecode/voice-gateway/scripts/gateway_voice_duplex.py:110)

| 工程 | 应有职责 | 当前实现判断 |
|---|---|---|
| `runner` | 人格、推理、会话、记忆、工具决策 | 通用底座较完整，但新旧会话路径、取消和上下文传递仍有缺口 |
| `voice-gateway` | 语音交互状态机、STT/TTS、文本桥接、打断 | 已有真实厂商适配；实时性和取消链路尚未闭合 |
| `minion-body` | 硬件访问、动作执行、播放同步、本地保护 | 眼睛串口已有实现；舵机、摄像头仍主要是模拟 |
| `minion-eyes` | 独立渲染、表情、眨眼、断联自治 | 边界最清晰；已有本地动画和活性看门狗，`LOOK` 尚未实现 |

runner 内部以 `agent-kernel` 为基础、扩展模块向下依赖、`agent-web` 统一装配，这个方向值得保留。原始音频不进入 JVM、眼睛渲染下沉 MCU、离散动作走 Tool/MCP，也都是适合这个项目的选择。

### 二、软件与实体设计，需要先统一一份现行方案

硬件设计里，以下原则很明确，也与软件分层相呼应：

- PETG 受力骨架与 PLA 装饰外壳分离。
- 腰转＋双臂，轴承承载，舵机提供转矩。
- 两只 ESP32 自行画眼睛，Pi 只下高层指令。
- 独立声腔与通风电子仓分离。

但文档之间已经出现会影响实施的分歧：

| 方面 | 设计材料 | 当前代码／其他文档 | 判断 |
|---|---|---|---|
| 部署 | 早期 Pi 跑完整对话栈 | runner ADR 改为远端大脑；语音脚本仍本地组合 | 需要明确当前支持的部署方式 |
| 眼睛 | LVGL、支持看向指令 | Arduino GFX 自绘；`LOOK` 返回 OK 但不执行 | 换渲染库没问题；能力状态必须准确 |
| 舵机 | STS3215 总线，明确弃用 PWM | runner 身体文档仍写 `gpiozero/PWM`，真实总线驱动未接 | 实现应以现选硬件为准 |
| 音频 | 独立 I2S DAC＋功放 | 当前脚本使用 ReSpeaker 输出链 | 必须重新确认 AEC 参考信号路径 |
| 嘴形 | 软件规划 viseme 三轨同步 | 实体“嘴”目前是喇叭格栅，没有明确口型执行器 | 不宜把口型系统当作当前必做项 |

依据：[硬件决策](/Users/hello/Documents/小黄人/files/decisions.md:28)、[身体模块文档](/Users/hello/Documents/sourcecode/runner/docs/modules/minion-body.md:22)、[眼睛指令实现](/Users/hello/Documents/sourcecode/minion-eyes/src/main.cpp:35)。

尤其注意两个实体问题：

**装配图需要重新校核。** v0.3 图里的麦克风仍是 ReSpeaker HAT，现选已经换成 USB 四麦阵列。因此图上“装配通过、净间隙约 14mm”的结论，不能直接当成现行硬件组合的验证结果。[装配图](/Users/hello/Documents/小黄人/files/minion-assembly-v0.3.svg)

**AEC 不能只看麦克风有没有这个功能。** Seeed 官方示例要求把 ReSpeaker 设为播放设备，并明确展示 playback 参考通道。由此推断，切换到独立 I2S 输出后，需要明确如何提供回声参考，不能默认原来的全双工效果仍然成立。这会影响功放连接和后续声学测试。[官方 AEC 示例](https://github.com/Seeed-Studio/wiki-documents/blob/docusaurus-version/sites/en/docs/Seeed_Elderly/reSpeaker/Extension_Board/ReSpeaker_Mic_Array_v2.0.md)

### 三、联合分析发现的主要问题

#### 1. 优先修：打断没有形成端到端闭环

这不是单个函数的问题，而是几层行为没有对齐：

- **新话没有交接。** 打断监控识别到文本就触发取消，但不保留这段文本／音频交给下一轮；状态机随后重新收音，容易丢失用户插话的开头。
- **本地播放没有立即中止接口。** `AplayPlayer.close()` 只是关闭 stdin 并等待进程退出，没有主动丢弃待播音频。
- **等待中的流水线不能及时响应取消。** `should_stop()` 要等下一块文本或音频返回后才能检查。
- **两端对结束的理解不同。** Java 打断入口只回 `speech_interrupted`；Python 文本桥收到后继续等待 `stream_end`。

最后一点尤其关键：Python 现有测试里的假 runner 会额外发 `stream_end`，而真实 Java 路径不这样做，导致两边测试分别通过，拼起来仍可能卡住。[Java 打断入口](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/websocket/SpringChatWebSocketHandler.java:614)、[Python 文本桥](/Users/hello/Documents/sourcecode/voice-gateway/src/voice_gateway/bridge.py:83)、[播放器关闭](/Users/hello/Documents/sourcecode/voice-gateway/src/voice_gateway/player.py:120)

我用真实 WebSocket 客户端加模拟远端复现了：

```text
收到打断确认、没有 stream_end → 文本桥仍等待下一块
```

应把打断定义为一个完整操作：停止生成、停止合成、丢弃待播音频、清空旧动作、保留新输入，并明确旧轮终止。

#### 2. 优先修：消息缺少可靠的轮次归属

共享 JSON Schema 是好的开始，runner 与 voice-gateway 的 schema 副本目前也一致。

但 schema 主要统一了消息形状，没有统一生命周期。持久连接通过替换 `_current_q` 切换轮次；消息到达时直接进入“当前队列”，无法识别它是否属于刚被放弃的旧轮。[消息分发](/Users/hello/Documents/sourcecode/voice-gateway/src/voice_gateway/ws_client.py:193)

探针复现结果：

```text
开始 B 轮后，收到延迟的 A 轮内容及结束帧
B 轮实际消费：['A:late']
```

因此需要统一 `sessionId → turnId/runId → chunk sequence`，并明确正常结束、取消、失败、断线的终态。字段升级应兼容现有公共契约。

这是单台机器人也需要的机制，不是多用户平台才需要的复杂度。

#### 3. 优先修：当前延迟指标不能证明“自然对话”

正常收音路径是：

```text
先收完整句 → 等端点静音 → 把缓存音频送 STT → runner → TTS
```

也就是说，虽然 STT 适配器叫 `stream_realtime`，主入口却没有边说边上传；默认端点静音窗口约 700ms。[收音路径](/Users/hello/Documents/sourcecode/voice-gateway/scripts/gateway_voice_duplex.py:138)

另外，脚本的首声计时从 `respond()` 开始，已经排除了收音和 STT；终点又记在调用 `player.write()` 之前，不能等同于喇叭真正出声。[计时位置](/Users/hello/Documents/sourcecode/voice-gateway/scripts/gateway_voice_duplex.py:195)

TTS 回调模式本身支持流式返回，不应误判成“全部 TTS 都在等整句合成”。[官方 SDK 实现](https://github.com/dashscope/dashscope-sdk-python/blob/main/dashscope/audio/tts_v2/speech_synthesizer.py)

建议统一测量“用户最后一个语音采样 → 实际开始播放”，分解端点、STT、首可说块、TTS 首包及播放缓冲耗时，再判断 0.9–1.3 秒目标是否达到。

#### 4. 表情还没有真正跟着声音走

当前存在两套设计：

- 设计：逐句 emotion → 本地 Realizer → 跟随播放时间改变表情。
- 实际入口：人格提示要求先调用 `set_eyes` 工具，然后再说话。

语音主入口没有把 `on_event` 接到 Realizer，`on_chunk` 也只用于记录回复。因此 Realizer 虽然有代码和测试，却不在当前主对话执行链上。[人格要求](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/config/OrchestratorConfig.java:38)、[语音接线](/Users/hello/Documents/sourcecode/voice-gateway/scripts/gateway_voice_duplex.py:208)

结果是：表情多一次工具往返，且变化时间由模型／网络决定，而不是对应语句的实际播放时间。

建议：

- 日常情绪随语音块交给本地 Realizer。
- 明确的“眨眼、看左边”等请求继续保留工具入口。
- 两种来源最终由同一个本地执行器协调。
- 时间线需要包含真实音频时长；目前仅靠事件最大 offset，音频-only 适配器会缺失最后一段时长。

#### 5. 接真实身体前，先让能力声明真实可信

`--eyes` 路径虽然连接真实眼睛，但其余动作仍委托 `LoudFakeDevice`。它会在日志里警告“不是硬件”，却仍向调用方返回 `waved`、`nodded` 等成功结果；`look` 还是固定文本标记。[模拟设备](/Users/hello/Documents/sourcecode/minion-body/src/minion_body/device.py:93)

这意味着：开发者知道没动，runner 却可能认为动作已完成。

还存在实体能力不匹配：当前机械目标是腰转＋双臂，而工具已经提供转头、点头，颈部却仍是可选项。

需要区分：

- 已安装并可执行。
- 暂不可用／断联。
- 明确的模拟模式。

工具接受、指令发送、硬件完成，也不能混为一个“成功”。接舵机时再补本地限位、速度限制、超时和停止机制；`RiskLevel.SYSTEM` 不是物理保护措施。

#### 6. 陪伴记忆需要与“用户实际听到的内容”一致

runner 原生路径的会话历史目前写入内存；长期记忆文件是另一条路径。历史查询和清空又仍委托旧 `SoulComfortChatService`，尚未统一存储和会话键。[会话存储](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/config/FileMemoryAdapter.java:40)、[历史接口](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/gateway/GatewayService.java:31)

放到机器人系统里，还多出一层区别：

> 模型生成了什么，不等于设备播放了什么，也不等于用户听到了什么。

当前打断记忆按已生成文本记录，语音日志按已收到的块记录。若中途取消，后续对话可能默认用户已经听过实际没播出的内容。

建议会话历史统一归 runner 管理，设备回报播放进度／打断位置；本地 JSONL 用于诊断，不承担第二套对话真相。

### 四、建议收敛成什么架构

我建议保留四仓，但先确定**一个受支持的 Pi 运行入口**，组合 voice-gateway 与 minion-body，不急于增加独立网络服务。

职责收敛为：

- **runner 决定内容和意图**：说什么、什么情绪、请求什么动作、记住什么。
- **voice-gateway 管对话轮次**：收音、转写、合成、跟进窗口和取消协调。
- **minion-body 管实际执行**：播放进度、表情调度、硬件状态及保护。
- **minion-eyes 管连续渲染**：动画细节、眨眼和断联自治。

其中必须明确：麦克风只有一个采集者，播放器只有一个控制者，串口只有一个执行入口；取消要跨越全部输出通道。

系统级文档也应有唯一入口，标记哪些早期设计已被替代，引用各仓细节，并记录可配套运行的代码、固件和协议版本。

### 五、下一阶段的推进顺序

1. **闭合语音轮次与打断**：新话不丢、旧话不串、不再播放旧音频、断线能够退出等待。
2. **验证真实响应时间**：正常输入改为边采边转，统一首响测量口径。
3. **把眼睛接入实际播放链**：先做好听、想、说、被打断这几种状态，再扩展复杂动作。
4. **统一会话与播放记录**：重启后续聊、查询、清空、打断后的上下文一致。
5. **接入真实总线舵机并重做装配验证**：同步确认音频输出／AEC、声腔、走线、电源与热余量。

每一步都应有跨仓场景验收，尤其覆盖“首块尚未返回时打断”“播放中连续插话”“旧帧晚到”“断网”“眼睛掉线”，不能只验证正常聊天。

### 六、验证范围与总结

- runner：前一轮 `mvn clean test` 全模块构建通过。
- voice-gateway：130 项测试，125 通过、5 项因缺可选依赖跳过。
- minion-body：120 项测试，115 通过、5 项因缺可选依赖跳过。
- 额外跨仓探针复现了打断等待、旧轮污染新轮，以及播放器仅正常关闭的行为。[探针](/tmp/minion_system_review_probe.py)
- minion-eyes：完成静态审阅；当前环境没有 PlatformIO，未构建固件、未验证实机。

**总结：结构分层已经基本找对，下一步应把“一次对话”的生命周期贯穿四个工程。** 当它能及时开口、完整接住插话、眼睛跟着声音变化，并准确记住双方实际交流过的内容时，才真正形成了这个项目最重要的系统能力。


## 第三轮：目标架构、生命周期和验收方案

继续深入后，改造方向可以收敛为：**在 Pi 上建立一个明确的“对话轮次协调层”，统一收音、生成、播放和身体执行的生命周期。** 保留四仓，不需要先增加第五个服务。

下面是进一步核查后的发现和目标设计；仍未修改工程代码。

### 一、补充确认的边界问题

这次用本地探针验证了四种异常场景：

| 场景 | 当前实际结果 | 应有行为 |
|---|---|---|
| 一段收音产生两个稳定识别句 | 只取第一句，后面的补充条件丢失 | 聚合本次发言，再提交完整输入 |
| 音频订阅队列已满，此时关闭订阅 | 关闭操作阻塞在向队列添加结束标记 | 关闭不依赖队列还有空位 |
| TTS 合成全部失败 | 返回 `(0, False)`，没有明确失败状态 | 返回合成失败，不能按正常应答收尾 |
| 没发现任何眼睛设备 | 仍返回 `eyes:happy` | 明确报告设备不可用 |

例如输入被识别为：

```text
请明天提醒我。
时间改成下午三点。
```

当前主入口会在第一个 `final` 处退出，只保留第一句。根因是把“某个句子的识别结果已稳定”当成了“用户这轮发言结束”。[识别映射](/Users/hello/Documents/sourcecode/voice-gateway/src/voice_gateway/aliyun_stt.py:37)、[主入口消费](/Users/hello/Documents/sourcecode/voice-gateway/scripts/gateway_voice_duplex.py:151)

其余结果分别对应：[订阅关闭](/Users/hello/Documents/sourcecode/voice-gateway/src/voice_gateway/frames.py:111)、[合成异常处理](/Users/hello/Documents/sourcecode/voice-gateway/src/voice_gateway/pipeline.py:140)、[空设备广播](/Users/hello/Documents/sourcecode/minion-body/src/minion_body/eyes.py:278)。[本轮探针](/tmp/minion_boundary_review_probe.py)

另外，上一轮“把 emotion 接入 Realizer”的建议还需要补一个前提：**当前仓库默认装配没有实际情绪分类器，分句输出默认都是 `neutral`。** 首块提前输出策略虽然已经实现，也没有在这份装配中启用。存在接口，不代表这些能力已进入实际运行链。[装配代码](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/config/PostProcessorConfig.java:75)

### 二、首先分清四种“结束”

这是整个系统最关键的概念模型。

| 边界 | 意义 | 谁能确认 |
|---|---|---|
| 识别片段稳定 | 一小段文本不再变化 | STT |
| 用户发言结束 | 本次输入可以完整提交 | Pi 的输入协调器 |
| 大脑生成结束 | 不再产生文本或工具事件 | runner |
| 应答播放结束 | 音频已经播放完毕 | Pi 的播放器 |

它们不能互相替代。

```text
用户输入： ─── 收音、转写 ─── 提交
runner：                       ─── 生成文本 ─── 生成结束
播放器：                            ─── 播放 ───────── 播放结束
下一次输入：                              ── 用户插话 ──
```

因此，全双工不应只是一个线性的：

```text
听 → 想 → 说 → 再听
```

而应同时维护三组状态：

- 输入：待机、收集中、已提交。
- 生成：运行中、完成、取消、失败。
- 播放：缓冲中、播放中、完成、取消、失败。

例如，“生成已完成，但播放器还在播放”完全正常。此时用户插话，**即使 runner 已无活跃任务，本地也必须立即停止旧应答**。

### 三、明确每件事由谁负责

建议新增的 `TurnCoordinator` 是一个本地应用层对象，不是新服务。可以放在 voice-gateway 的正式运行入口中，组合现有组件。

| 对象 | 唯一负责人 | 边界 |
|---|---|---|
| 对话轮次、输入提交、打断协调 | voice-gateway | 不直接控制具体舵机 |
| 人格、推理、工具决策、会话记忆 | runner | 不判断喇叭是否已播完 |
| 音频设备、播放队列、播放进度 | minion-body 的音频执行层 | 不决定下一轮说什么 |
| 表情和动作调度、设备状态 | minion-body | 不重新调用 LLM/TTS |
| 眼睛渲染、自治眨眼 | minion-eyes | 不承担对话状态机 |

现有 voice-gateway 播放接口可以保留兼容适配，逐步把实际硬件控制收敛到一个执行入口。

这里还要避免两种重复控制：

1. MCP 工具和 Realizer 都直接写串口，互相覆盖。
2. Pi 定时眨眼与 ESP 自治眨眼各自调度。

更合理的是：MCP 与 Realizer 都向同一个身体执行器提交意图；ESP 负责动画细节，Pi 负责模式和显式动作。

### 四、跨仓协议应增加哪些语义

下面是建议契约，不是当前已有实现。

#### 1. 轮次身份不能依赖连接或队列

最低限度需要：

- `sessionId`：持续对话身份，重连后可以保持。
- `turnId`：一次用户发言及其应答的身份，不复用。
- `segmentId`／序号：这轮中的某个可说块。
- `runId`：runner 内部执行身份，不能替代用户轮次。

尤其是当前 runner 的隐式打断路径可能复用执行对象，而独立打断后又走新建分支，因此不能直接假设一个 `runId` 永远对应一种稳定的用户轮次语义。[编排分支](/Users/hello/Documents/sourcecode/runner/agent-kernel/src/main/java/com/lightweightai/kernel/orchestrator/Orchestrator.java:183)

#### 2. 取消必须有独立控制路径

一次确认的插话应触发：

```text
持续保存新输入
       │
       ├─ 本地立即作废旧轮输出，唤醒所有等待者
       ├─ 停止旧音频，清除旧表情／动作队列
       └─ 通知 runner 取消旧轮，异步处理确认
```

关键约束：

- 本地停播不等待云端确认。
- 取消信号不排在大量音频之后。
- 已取消轮次的迟到消息全部忽略。
- 重复取消是幂等操作。
- 取消语音不意味着撤销已经完成的工具副作用。
- 断线时可以本地结束失败轮次，不能无限等待远端终态。

播放器接口也需要明确区分：

- `drain`：正常播放完剩余内容。
- `abort`：立即丢弃待播内容。
- `close`：释放资源。

一个 `close()` 承担三种含义，正是当前问题的来源之一。

#### 3. 音频数据必须自带格式和时间信息

当前网关默认输出 22050Hz，身体音频对象默认 16000Hz；主链路目前使用匹配的播放器，但以后接 Realizer 时，不能靠默认值完成转换。

音频块至少要携带采样率、声道、格式、帧数及所属轮次／语句。播放进度应来自输出端，必要时显式处理重采样，不能用“收到多少字节”代替“已经播放多久”。

还有一个需要修正的表述：设备只能确认“已经播放”，不能确认用户真的听见或理解了。没有字词级对齐时，中途打断的句子应该标记为“部分播出”，不能按音频比例猜测精确播到了哪个字。

#### 4. 兼容升级要在设计阶段处理

保留现有 `Tool`、`ToolSourceProvider` 和公共 MCP API。新增生命周期语义通过明确的 `StreamEvent.EventType` 和工厂表达，不塞进 `TRACE` 字符串。

部分现有 schema 禁止附加字段，因此不能简单给所有帧直接加字段；需要协议版本／能力协商和兼容适配。

### 五、失败与降级也属于产品设计

当前部分代码采用“记录日志，然后继续”。对于机器人，这会变成用户面对它却不知道发生了什么。

建议规定：

- **STT 失败**：这一轮没有可信输入，不向 runner 提交半成品。
- **TTS 失败**：记录未播出；输出层仍可用时，播放预置的简短故障提示。
- **runner 断线**：本地结束等待，恢复可交互状态；不自动重放可能产生副作用的旧请求。
- **眼睛断线**：语音可以继续，但能力状态要更新，不能返回表情执行成功。
- **动作结果不确定**：报告“不确定”，不能透明重试非幂等动作。
- **特殊业务拦截**：返回语音端可处理的结果及终态。

最后一点已有实际例子：Spring 的安全拦截只发 `crisis_alert` 就返回，Python 文本桥没有对应结束处理。网页能够显示一个提示，不代表语音终端能够正确回应。[拦截分支](/Users/hello/Documents/sourcecode/runner/agent-web/src/main/java/com/lightweightai/web/websocket/SpringChatWebSocketHandler.java:560)

待机隐私也需要明确：当前是候选语音先上传 STT，再判断是否包含唤醒词；并非“本地唤醒之后才上传”。这可以作为联调模式，但长期待机时应明确选择本地唤醒或云端关键词门控，并记录这个取舍。[当前唤醒实现](/Users/hello/Documents/sourcecode/voice-gateway/src/voice_gateway/wake.py:1)

### 六、最先落地的验收场景

我建议第一项改造就围绕下面这个完整场景，而不是分别给四仓增加更多单元测试：

> A 轮正在等待首段回答，用户插话 B；B 包含两段稳定识别结果。runner 确认取消 A，随后还有 A 的迟到消息。系统应完整提交 B 一次，不再播放 A，也不遗留等待线程。

验收必须检查下游真实收到的内容：

1. B 的两个识别片段都进入 runner，且只提交一次。
2. 取消后，旧轮不能再向播放器和执行器提交新内容。
3. 不收到 `stream_end` 也能退出被取消的等待。
4. A 的迟到结束帧不能结束 B。
5. 队列满、SDK 异常、连接关闭都能收尾。
6. 日志区分生成完成、播放完成、取消和失败。

用真实 Java WebSocket 路径和真实 Python 桥接代码驱动，远端模型、语音服务及硬件用受控对端替代；通过事件和虚拟播放时钟控制顺序，不靠睡眠碰运气。

**这一步完成后，再接逐句情绪、播放时钟和真实舵机，才有稳定的系统基础。当前最值得投入的是把这些“完成边界”做成明确协议，而不是继续增加抽象接口。**

## 与后续实施的关系

上面第三轮的首个完整验收场景，随后成为
[ADR-015：轮次关联与取消](../decisions/015-voice-turn-cancellation.md)
的实施范围；协议说明见
[Minion 语音文本契约](../../contract/minion-voice-text/README.md)。

ADR-015 聚焦轮次、终态、本地取消与插话交接，不代表本文列出的
runner 工具作用域／鉴权、历史和 Skill、播放时钟、真实硬件等问题已经全部解决。
后续阶段应分别维护验收证据与完成状态，不以第一阶段的通过替代整个系统验收。
