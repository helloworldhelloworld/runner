# Minion Eyes（ESP32-S3 自渲染眼睛）

> 独立程序，**不在本 Java 仓内**、**非 JVM**，**ESP32-S3 固件（C++）**。Minion 的"眼睛"。
> 设计依据见 [ADR-009](../decisions/009-minion-eyes-esp-directive.md)（修订 [ADR-006](../decisions/006-minion-embodiment-architecture.md) D4 的眼睛部分）。

## Responsibility

眼睛 = 小黄人**主表情通道**。每只眼睛是一块 **Waveshare ESP32-S3-Touch-AMOLED-1.43-B**
（板载 1.43" AMOLED，466×466，CO5300/SH8601 类 QSPI 控制器）。ESP **自治渲染**——跑自己的
动画时钟，idle 眨眼/微动/呼吸、情绪表情、表情平滑过渡都在 MCU 上算，**独立于 Pi 负载**
（Pi 跑 LLM/音频时眼睛也不卡）。Pi 只下高层 **directive**，不画像素。

## Position（在系统里的位置）

```
runner(脑)  ──MCP set_eyes──►  minion-body(Pi)  ──USB串口 directive──►  2× ESP32-S3(眼睛)
                                SerialEyesLink        EMOTION/BLINK/IDLE      板载自渲染 AMOLED
```

- runner 只看到 `set_eyes`(WRITE) MCP 工具，**对眼睛硬件无感**（ADR-009 D4）。
- minion-body 的 `set_eyes` 实现 = 往 ESP 串口写一行 directive（替掉 FakeDevice 的 print）。
- ESP 收 directive → 在 AMOLED 上渲染对应表情/动作。

## 与 Pi 的契约（directive 协议，ADR-009 D2）

USB CDC 串口（`/dev/ttyACM*`），`115200 8N1`，**换行结尾 ASCII 行**。人可读、可 `echo > /dev/ttyACM0` 手测。

| 方向 | 内容 |
|---|---|
| Pi → ESP | `EMOTION <name>`（neutral/happy/cheerful/sad/angry/surprised/sleepy…）· `LOOK <dir\|x,y>` · `BLINK` · `IDLE on\|off` · `HELLO`/`PING` |
| ESP → Pi | `OK` / `ERR <reason>`；`HELLO` 回 `HELLO eyes/<role> v<protocol>` |

- 双眼各一条 USB；每只 ESP 烧入身份 `left`/`right`，`HELLO` 回报；`LOOK` 水平分量由 ESP 按身份镜像。
- 协议以 ADR-009 命令表为单一出处 + `EYES_PROTOCOL` 版本号；不兼容变更升版，`HELLO` 协商。

## 本期范围（ADR-009「待确认」第 4 条）

先 **EMOTION + BLINK + IDLE**：点亮、会表情、会眨、idle 有微动。`LOOK`（注视）押后，待视觉给注视点。

## Rules

- 渲染只在 ESP；Pi 不画像素、不碰 QSPI。
- ESP 拔掉/重启时，Pi 侧 `set_eyes` 串口写**快失败不挂起**（集成-seam 规则）；directive 先 fire-and-forget + 周期 `HELLO` 保活。
- 屏纯显示、无机械风险，不涉物理安全反射（ADR-006 D7）。

## 选型参考

- AMOLED 起底：Waveshare 例程 / LovyanGFX 或 LVGL。
- 表情引擎：RoboEyes 风格状态机（情绪 + 眨眼 + idle + 平滑过渡）自绘。
