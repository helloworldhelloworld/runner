# 解忧杂货铺 - Agent Web 🏮

基于Spring Boot的心灵引导Agent Web服务，支持记忆和反思能力。

## 快速开始

### 方式一：自动获取Session Key（最简单）⭐

使用你的**Claude Pro会员**，完全自动化！

```bash
# 1. 自动获取Session Key（从浏览器）
python3 get-session-key.py

# 2. 加载环境变量
source .env

# 3. 启动服务
./start-with-pro.sh

# 4. 测试
python3 test_soul_comfort.py
```

**仅需10秒！** 脚本自动从Chrome/Edge/Firefox提取sessionKey。

### 方式二：使用官方API

适合生产环境，最稳定可靠。

```bash
# 1. 获取API Key
# 访问 https://console.anthropic.com/

# 2. 设置环境变量
export PROVIDER_TYPE=api
export ANTHROPIC_API_KEY=sk-ant-your-api-key

# 3. 启动服务
./start-with-api.sh
```

### 方式三：Mock模式（测试）

无需任何配置，用于开发测试。

```bash
# 直接启动（默认Mock模式）
mvn spring-boot:run

# 测试
curl --noproxy localhost -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"测试消息","sessionId":"test","soulComfortMode":true}'
```

## 三种模式对比

| 模式 | 配置 | 费用 | 响应质量 | 适用场景 |
|------|------|------|----------|---------|
| **Auto Pro** | 自动获取 | Pro会员 | 真实AI | 个人使用（推荐）|
| **API** | API Key | 按量付费 | 真实AI | 生产环境 |
| **Mock** | 无需配置 | 免费 | Mock文本 | 开发测试 |

## 功能特性

### ✅ 心灵引导Agent

- 🧠 **记忆能力** - 记住用户的对话历史和个人信息
- 💭 **反思能力** - 深度理解用户的情绪和需求
- ❤️ **共情能力** - 提供温暖、包容的回应
- 🎯 **引导能力** - 帮助用户理清思路，找到答案

### API端点

```bash
# 心灵引导对话
POST /api/chat
{
  "message": "我今天压力好大...",
  "sessionId": "user-001",
  "soulComfortMode": true
}

# 获取会话摘要
GET /api/session/{sessionId}/summary

# 清空会话
DELETE /api/session/{sessionId}

# 健康检查
GET /api/health
```

## 详细文档

### 配置指南

- **[AUTO_SESSION_KEY.md](../AUTO_SESSION_KEY.md)** - 自动获取Session Key（推荐）⭐
- **[CLAUDE_API_SETUP.md](../CLAUDE_API_SETUP.md)** - 官方API配置指南
- **[CLAUDE_PRO_INTEGRATION.md](../CLAUDE_PRO_INTEGRATION.md)** - Claude Pro集成详解
- **[CLAUDE_PRO_QUICKSTART.md](../CLAUDE_PRO_QUICKSTART.md)** - 快速开始指南

### 技术文档

- **[SOUL_COMFORT_AGENT.md](../SOUL_COMFORT_AGENT.md)** - Agent架构和功能说明

## 项目结构

```
agent-web/
├── src/main/java/
│   └── com/lightweightai/web/
│       ├── controller/
│       │   └── ChatController.java       # API端点
│       ├── service/
│       │   ├── ChatService.java          # 普通聊天服务
│       │   └── SoulComfortChatService.java  # 心灵引导服务
│       ├── model/
│       │   ├── ChatRequest.java          # 请求模型
│       │   └── ChatResponse.java         # 响应模型
│       └── config/
│           └── AgentConfig.java          # LLM配置
├── src/main/resources/
│   ├── application.yml                   # 应用配置
│   └── static/
│       └── index.html                    # Web UI
├── get-session-key.py                    # Session Key自动获取 ⭐
├── test_soul_comfort.py                  # 测试脚本
├── start-with-api.sh                     # API模式启动
├── start-with-pro.sh                     # Pro模式启动
├── .env.example                          # 环境变量模板
└── README.md                             # 本文件
```

## 配置说明

### application.yml

```yaml
app:
  provider-type: mock  # 可选: mock, api, pro
  claude:
    # API模式
    api-key: ${ANTHROPIC_API_KEY:}
    model: claude-3-5-sonnet-20241022

    # Pro模式
    session-key: ${CLAUDE_SESSION_KEY:}
    organization-id: ${CLAUDE_ORG_ID:}
```

### 环境变量

```bash
# Mock模式（默认）
# 无需设置

# API模式
export PROVIDER_TYPE=api
export ANTHROPIC_API_KEY=sk-ant-xxx

# Pro模式
export PROVIDER_TYPE=pro
export CLAUDE_SESSION_KEY=xxx
```

## 开发

### 构建

```bash
# 编译
mvn clean compile

# 打包
mvn clean package

# 跳过测试
mvn clean package -DskipTests
```

### 运行

```bash
# 开发模式（热重载）
mvn spring-boot:run

# 生产模式
java -jar target/agent-web-0.1.0-SNAPSHOT.jar
```

### 测试

```bash
# 健康检查
curl --noproxy localhost http://localhost:8080/api/health

# 完整测试
python3 test_soul_comfort.py
```

## 常见问题

### Q: 如何选择模式？

- **开发/测试** → Mock模式
- **个人使用** → Auto Pro模式（推荐）
- **生产环境** → API模式

### Q: Session Key会过期吗？

是的，通常90天。使用 `python3 get-session-key.py` 重新获取。

### Q: API费用如何？

Claude Sonnet 3.5约$0.0045/次对话。100次对话约$0.45。

### Q: Mock模式的限制？

Mock模式返回固定文本，无真实AI能力。仅用于测试UI和基础功能。

## 成本对比

| 模式 | 月费用 | 单次对话 | 特点 |
|------|--------|----------|------|
| Mock | 免费 | 免费 | 测试用 |
| Pro | $20 | 无限 | 个人推荐 |
| API | 按量 | ~$0.005 | 生产推荐 |

## 依赖项目

- **agent-kernel** - 核心Agent框架
  - LLM Provider抽象
  - Memory系统
  - Reflection服务

- **harmony-client** - HarmonyOS客户端
  - 纯血鸿蒙应用
  - 极简Apple风格

## License

MIT License

## 支持

- 📖 查看文档：`/docs` 目录
- 🐛 报告问题：GitHub Issues
- 💬 讨论交流：GitHub Discussions

---

**从自动获取Session Key开始，3步启动你的心灵引导助手！** 🏮

```bash
python3 get-session-key.py && source .env && ./start-with-pro.sh
```
