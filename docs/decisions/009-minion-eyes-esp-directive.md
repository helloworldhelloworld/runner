# ADR-009: Minion 眼睛 — ESP32-S3 自渲染 + Pi 下 directive

## Status

Proposed (2026-06-13)。**修订 [ADR-006](006-minion-embodiment-architecture.md) D4/D5 中"眼睛 = Pi 直驱圆形 OLED + RoboEyes"那部分**——其余（三轨 Realizer、媒体/大脑双平面、脑在云）不变。

## Context

ADR-006 D4 把眼睛定为小黄人**主表情通道**（圆形 OLED + 动画库如 FluxGarage RoboEyes），D5 让设备端 Realizer 在 Pi 上驱动"眼睛(emotion)"。落地选型时（采购清单 v2）把眼睛换成了 **2× Waveshare ESP32-S3-Touch-AMOLED-1.43-B**（每只眼睛 = 一块带 1.43" AMOLED 的 ESP32-S3，板载自渲染），原因两条：

1. **Pi 自驱这块 AMOLED 走 QSPI 调试麻烦**（466×466，CO5300/SH8601 类控制器，QSPI 时序 + 厂商初始化序列）。
2. **Pi 在跑 LLM 推理 / 音频流时会抢 CPU，眼睛动画卡顿**——而眼睛是主表情通道，一卡顿"活着"的拟人感直接崩。

硬件现状：一只 ESP 已插上 Pi，识别为 `/dev/ttyACM0`（CDC ACM，`303a:1001` Espressif USB JTAG/serial）。

矛盾点：ADR-006 要"眼睛紧贴时钟、表情顺滑"，但 Pi 是个会被大脑/音频抢占的繁忙节点。解法是把眼睛渲染**再下沉一层到专用 MCU**。

## Decision

### D1. 眼睛渲染下放到 ESP（自治渲染器）

每只眼睛的 ESP32-S3 跑**自己的动画时钟**，独立于 Pi 负载：idle 眨眼/微动/呼吸、情绪表情、看向、表情间平滑过渡，全在 ESP 上算。这是 ADR-006「Realizer 紧贴时钟、放设备端」原则的延伸——把"眼睛"这一路从 Pi 再下沉到 MCU，保证无论 Pi 多忙，眼睛永远顺。

### D2. Pi↔ESP 走 USB 串口 + 行式 directive 协议（高层语义，非像素）

- **传输**：USB CDC 串口（`/dev/ttyACM*`），`115200 8N1`，**换行结尾的 ASCII 行**。理由：简单、可 `echo > /dev/ttyACM0` 手测、串口稳、无需自定义二进制帧。
- **命令集（Pi → ESP，大写动词 + 参数）**：

  | 指令 | 含义 | 来源 |
  |---|---|---|
  | `EMOTION <name>` | 设表情（neutral/happy/cheerful/sad/angry/surprised/sleepy…）| `set_eyes` 工具 / Realizer 情绪轨逐块 emotion |
  | `LOOK <dir>` 或 `LOOK <x>,<y>` | 注视方向（center/left/right/up/down，或归一化坐标）| 视觉/注视（本期可选）|
  | `BLINK` | 触发一次眨眼 | Realizer / idle |
  | `IDLE on\|off` | 开关自治 idle 微动 | 对话开始/结束 |
  | `HELLO` / `PING` | 握手 / 保活（带协议版本）| 连接生命周期 |

- **回执（ESP → Pi）**：`OK` / `ERR <reason>`；`HELLO` 回 `HELLO eyes/<role> v<protocol>`。
- **协议版本**：`HELLO` 协商；Pi 与 ESP 两端以本 ADR 的命令表为单一出处，加 `EYES_PROTOCOL` 版本号，不兼容变更须升版。

### D3. Realizer 情绪轨的角色变化：Pi 决定"何时"，ESP 决定"如何"

Pi 的 Realizer（minion-body B5）仍负责**时序**——按 LLM 流的逐块 emotion（ADR-006 R3 的 `SPEAKABLE_CHUNK.emotion`）/ `set_eyes` 触发，在正确时刻下发 `EMOTION` directive；**渲染（如何画、怎么过渡、idle 微动）全在 ESP**。Pi 不再画像素。

### D4. MCP 契约不变（脑不为具身硬件改动）

runner（脑）只看到 `set_eyes`（`RiskLevel.WRITE`）这个 MCP 工具。minion-body 内部把 `set_eyes(emotion)` 实现成"往 ESP 串口写一行 `EMOTION <emotion>`"——替掉现在 `FakeDevice` 的 `print`。runner / agent-mcp 对外契约零改动，符合 ADR-006「Public API Stability」与「脑在云、Pi 瘦身体」。

### D5. 双眼寻址

2× ESP 各占一条 USB（两个 `ttyACM`）。每只 ESP 烧入身份 `left`/`right`（编译期常量或上电脚位判定，`HELLO` 回报）。Pi 同时向两只下发 directive；`EMOTION`/`BLINK`/`IDLE` 两眼一致，`LOOK` 的水平分量由 ESP 按自身 `left`/`right` 身份处理（镜像在设备端，Pi 只发统一注视点）。

### D6. 代码归属（沿用多仓模式）

- **ESP 固件**：新仓 **`minion-eyes`**（C++，ESP-IDF 或 Arduino + LovyanGFX/LVGL 起 AMOLED；RoboEyes 风格表情自绘），与 `minion-body`/`voice-gateway` 并列的独立非 JVM 组件。
- **Pi 侧 directive 发送器**：落 `minion-body`（一个真 `Device` 实现，串口写 directive；fakes-first：先 `FakeEyesLink` 记录下发的行，再真 `SerialEyesLink`）。
- **设计文档**：本 ADR + 新增 [minion-eyes.md](../modules/minion-eyes.md) 模块文档，**留在 runner 仓**（架构单一出处），与 minion-body.md/voice-gateway.md 并列。

## Consequences

- **Positive**：眼睛永远顺——独立 MCU 跑自己的时钟，不被 LLM/音频抢占（直接消除 v2 列的"卡顿"风险）。Pi 免去 QSPI 屏驱的调试地狱。directive 是人可读、可 `echo` 手测的行协议，好调。**MCP 契约与 runner 侧零改动。**
- **Trade-off**：多一块 ESP 固件要维护（独立仓 + 烧录工具链）；双眼两条 USB 串口；directive 协议要 Pi/ESP 两端同步（靠 `HELLO` 版本协商兜底）。
- **掉电/断链**：ESP 拔掉/重启时，Pi 侧 `set_eyes` 串口写**快失败不挂起**（呼应 CLAUDE.md 集成-seam "post-disconnect fail fast"）；directive 先 fire-and-forget + 周期 `HELLO` 保活，ACK 重传按需再加。
- **Deferred（本期可不做）**：`LOOK` 注视（需视觉给注视点）——本期先做 `EMOTION`+`BLINK`+`IDLE` 把眼睛点亮、会表情、会眨；左右眼 `LOOK` 镜像策略；directive ACK 重传。
- **物理安全**：眼睛纯显示、无机械风险，不涉 ADR-006 D7 的物理安全反射。

## 待你确认的设计选择

1. **固件落新仓 `minion-eyes`** 还是塞进 `minion-body` 子目录？（建议新仓，跟现有多仓模式一致）
2. **directive 命令表**（D2）够不够、命名认不认可？
3. 屏型号最终确认 **ESP32-S3-Touch-AMOLED-1.43-B**？（决定 ESP 端用哪套 AMOLED 驱动例程）
4. 本期范围：先 **EMOTION + BLINK + IDLE 点亮 + 表情**，`LOOK` 押后——这个切片同意吗？
