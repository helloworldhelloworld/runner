# 解忧杂货铺 🏮

> 你的心灵陪伴者 - 纯血鸿蒙应用

## 项目简介

**解忧杂货铺**是一个基于纯血鸿蒙（HarmonyOS NEXT）开发的心灵引导聊天助手应用。采用极简的苹果风格设计，为用户提供温暖、治愈的对话体验。

### 设计理念

- **极简美学**: 借鉴苹果设计语言，清爽简洁的界面
- **温暖治愈**: 柔和的配色方案，营造舒适的聊天氛围
- **心灵陪伴**: 专注于情感支持和心理疏导

## 功能特性

### 已实现功能

✅ **极简聊天界面**
- 仿 iOS 风格的消息气泡
- 流畅的动画效果
- 自适应键盘

✅ **智能对话**
- 集成后端 AI 服务
- 支持长文本对话
- 实时消息反馈

✅ **优雅交互**
- 发送按钮动态状态
- 消息加载提示
- 自动滚动到最新消息

### 技术特点

- **纯血鸿蒙**: 基于 HarmonyOS NEXT 开发
- **ArkTS**: 使用鸿蒙原生开发语言
- **MVVM 架构**: 清晰的代码结构
- **REST API**: 与后端服务通信

## 项目结构

```
harmony-client/
├── entry/                          # 主模块
│   └── src/main/
│       ├── ets/                    # ArkTS 源代码
│       │   ├── entryability/       # 应用入口
│       │   │   └── EntryAbility.ets
│       │   ├── pages/              # 页面
│       │   │   └── ChatPage.ets    # 聊天主页面
│       │   ├── common/             # 通用组件
│       │   │   └── MessageItem.ets # 消息气泡组件
│       │   ├── model/              # 数据模型
│       │   │   ├── Message.ets     # 消息模型
│       │   │   └── ChatApi.ets     # API 模型
│       │   ├── service/            # 服务层
│       │   │   └── ChatService.ets # 聊天服务
│       │   └── viewmodel/          # 视图模型
│       │       └── ChatViewModel.ets
│       ├── resources/              # 资源文件
│       │   └── base/
│       │       ├── element/        # UI 元素
│       │       │   ├── color.json  # 颜色配置
│       │       │   └── string.json # 文本资源
│       │       ├── media/          # 媒体资源
│       │       └── profile/        # 配置文件
│       └── module.json5            # 模块配置
├── build-profile.json5             # 构建配置
└── oh-package.json5                # 包管理配置
```

## 设计规范

### 颜色方案

| 颜色用途 | 颜色值 | 说明 |
|---------|--------|------|
| 背景色 | `#FFFFFF` | 纯白背景 |
| 主文本 | `#1D1D1F` | 深灰色文字 |
| 次文本 | `#86868B` | 浅灰色文字 |
| 主题色 | `#F4D9C6` | 温暖的杏色 |
| 用户消息 | `#007AFF` | iOS 蓝色 |
| 助手消息 | `#F2F2F7` | 浅灰色背景 |
| 分割线 | `#E5E5EA` | 极浅灰色 |

### 字体规范

- **标题**: 20pt / Semi-bold
- **正文**: 16pt / Regular
- **副标题**: 13-15pt / Regular
- **辅助文字**: 14pt / Regular

### 间距规范

- 页面边距: 16px
- 消息气泡圆角: 20px
- 按钮圆角: 22px (圆形按钮)
- 组件间距: 8-12px

## 开发环境

### 必需工具

- **DevEco Studio**: 5.0+ (HarmonyOS NEXT 开发 IDE)
- **HarmonyOS SDK**: API 12+
- **Node.js**: 16.0+

### 后端服务

需要启动配套的后端服务：

```bash
cd ../agent-web
mvn spring-boot:run
```

后端服务地址配置在 `ChatService.ets`:

```typescript
private static readonly BASE_URL = 'http://localhost:8080';
```

## 运行步骤

### 1. 导入项目

1. 打开 **DevEco Studio**
2. 选择 **Open** 导入 `harmony-client` 目录
3. 等待项目同步完成

### 2. 配置设备

**选项 A: 使用模拟器**
- 在 DevEco Studio 中创建 HarmonyOS 模拟器
- 选择 API Level 12+ 的系统镜像

**选项 B: 使用真机**
- 连接鸿蒙手机
- 开启开发者模式
- 信任电脑连接

### 3. 启动后端服务

```bash
# 在项目根目录
cd agent-web
mvn spring-boot:run
```

确保后端服务运行在 `http://localhost:8080`

### 4. 运行应用

1. 在 DevEco Studio 中点击 **Run** 按钮
2. 选择目标设备
3. 等待应用安装和启动

## API 配置

如果需要修改后端服务地址，编辑：

`entry/src/main/ets/service/ChatService.ets`

```typescript
export class ChatService {
  // 修改为实际的后端地址
  private static readonly BASE_URL = 'http://your-server-ip:8080';
  // ...
}
```

### 支持的 API 端点

- `POST /api/chat` - 发送聊天消息
- `GET /api/skills` - 获取技能列表
- `GET /api/tools` - 获取工具列表
- `GET /api/health` - 健康检查

## 核心功能说明

### 聊天流程

1. **初始化**: 应用启动时检查后端连接状态
2. **欢迎消息**: 显示温暖的欢迎语
3. **用户输入**: 在底部输入框输入消息
4. **发送消息**: 点击发送按钮或按回车
5. **显示加载**: 显示"正在思考..."的加载提示
6. **接收回复**: 获取 AI 回复并显示
7. **消息历史**: 所有消息保存在聊天记录中

### 消息类型

- **用户消息**: 蓝色气泡，右对齐
- **助手消息**: 灰色气泡，左对齐，带灯笼图标 🏮
- **系统消息**: 居中显示，灰色文字

## 未来规划

- [ ] 消息持久化存储
- [ ] 多轮对话上下文
- [ ] 技能切换功能
- [ ] 情绪分析可视化
- [ ] 消息搜索功能
- [ ] 主题切换（深色模式）
- [ ] 语音输入支持
- [ ] 消息语音朗读
- [ ] 更多温暖的动画效果

## 设计哲学

### 为什么叫"解忧杂货铺"？

灵感来自东野圭吾的小说《解忧杂货店》，在小说中，杂货店是一个可以倾诉烦恼、寻求答案的温暖场所。我们希望这个应用也能成为用户心灵的港湾。

### 设计原则

1. **简洁至上**: 去除一切不必要的元素
2. **温暖治愈**: 柔和的配色和文案
3. **尊重隐私**: 本地处理，用户可控
4. **专注对话**: 核心功能是陪伴和倾听

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

本项目采用 MIT 许可证。

---

**愿每一个烦恼，都能在这里找到温柔的答案。** 🏮
