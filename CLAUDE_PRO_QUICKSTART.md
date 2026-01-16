# Claude Pro会员 - 快速使用指南 ⭐

## 最简单的方案：使用环境变量 + 会员SessionKey

由于当前的LLMProvider接口较复杂，完整集成ClaudeProProvider需要更多时间。但你可以用以下更简单的方式：

### 方案A：使用第三方代理（推荐）⭐

**工作原理**：使用第三方服务将Claude Pro的session key转换为兼容的API格式

**步骤**：

1. **获取Session Key**（和之前一样）
   - 访问 https://claude.ai 并登录
   - F12 → Application → Cookies → sessionKey
   - 复制值

2. **使用claude-api-py**（Python转发服务）
   ```bash
   # 安装
   pip3 install claude-api

   # 设置Session Key
   export CLAUDE_SESSION_KEY="your-session-key"

   # 启动转发服务（监听在 localhost:3000）
   claude-api-server --port 3000
   ```

3. **配置应用连接到转发服务**
   ```yaml
   # application.yml
   app:
     provider-type: api
     claude:
       api-url: http://localhost:3000  # 使用本地转发
       api-key: ${CLAUDE_SESSION_KEY}
   ```

### 方案B：使用官方API（最稳定）

如果你已有Claude Pro会员，可以：

1. **登录Anthropic Console**
   - https://console.anthropic.com/
   - 使用同一个账号登录

2. **查看是否有API额度**
   - Pro会员可能有免费API额度
   - 查看 Usage 页面

3. **创建API Key并使用**
   ```bash
   export PROVIDER_TYPE=api
   export ANTHROPIC_API_KEY=sk-ant-your-key
   ./start-with-api.sh
   ```

### 方案C：修改为简化的MockProvider

暂时继续使用Mock模式测试UI和基础功能，等待后续完整集成：

```bash
# 默认就是Mock模式
cd agent-web
mvn spring-boot:run
```

## 当前状态说明

### ✅ 已完成
1. 完整的Soul Comfort Agent（心灵引导）
2. 记忆和反思能力
3. HarmonyOS纯血鸿蒙客户端
4. Web后端API
5. 三种模式切换（Mock/API/Pro）
6. 配置文件和启动脚本

### 🚧 进行中
- ClaudeProProvider完整实现（需要适配新的LLMProvider接口）
- 当前的LLMProvider使用了更复杂的ContentBlock系统
- 需要将claude.ai的SSE响应转换为ContentBlock格式

### 📋 推荐使用方式（按优先级）

| 方案 | 适用场景 | 复杂度 | 费用 |
|------|---------|--------|------|
| **Mock模式** | UI/功能测试 | ⭐ 最简单 | 免费 |
| **官方API** | 生产使用 | ⭐⭐ 简单 | 按量付费 |
| **第三方代理** | 使用Pro额度 | ⭐⭐⭐ 中等 | Pro会员费用 |
| **完整Pro集成** | 深度定制 | ⭐⭐⭐⭐⭐ 复杂 | Pro会员费用 |

## 快速开始（Mock模式）

**适合**：测试界面、验证功能、开发调试

```bash
# 1. 进入项目目录
cd /Users/hello/Documents/sourcecode/runner/agent-web

# 2. 启动服务（默认Mock模式）
mvn spring-boot:run

# 3. 测试
curl --noproxy localhost -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "我今天压力好大...",
    "sessionId": "test-001",
    "soulComfortMode": true
  }'
```

## 快速开始（API模式）

**适合**：获得真实Claude回复，官方支持

```bash
# 1. 获取API Key
# 访问 https://console.anthropic.com/ 创建

# 2. 设置环境变量
export PROVIDER_TYPE=api
export ANTHROPIC_API_KEY=sk-ant-your-api-key-here

# 3. 启动服务
cd agent-web
./start-with-api.sh

# 4. 测试真实对话
python3 test_soul_comfort.py
```

## HarmonyOS客户端使用

```bash
# 进入鸿蒙项目
cd harmony-client

# 配置后端地址（在entry/src/main/ets/service/ChatService.ets）
# 默认：http://localhost:8080

# 使用DevEco Studio打开项目
# 运行到模拟器或真机
```

## 后续计划

1. **完整实现ClaudeProProvider**
   - 适配新的LLMProvider接口
   - 支持ContentBlock系统
   - 完整的SSE流式响应

2. **增强功能**
   - Session Key自动刷新
   - 更好的错误处理
   - 连接状态监控

3. **文档完善**
   - API使用示例
   - 故障排除指南
   - 最佳实践

## 获取帮助

- **查看文档**：
  - `CLAUDE_API_SETUP.md` - 官方API配置
  - `CLAUDE_PRO_INTEGRATION.md` - Pro集成详情
  - `SOUL_COMFORT_AGENT.md` - Agent功能说明

- **常见问题**：
  - Mock模式回复是固定文本，用于测试
  - API模式需要从console.anthropic.com获取密钥
  - Pro模式正在完善中

- **推荐顺序**：
  1. 先用Mock模式熟悉功能
  2. 获取API Key使用API模式
  3. 等待Pro模式完整集成

## 总结

**目前最佳实践**：

```bash
# 开发/测试：使用Mock模式
mvn spring-boot:run

# 生产/真实使用：使用API模式
export PROVIDER_TYPE=api
export ANTHROPIC_API_KEY=your-key
./start-with-api.sh
```

**Claude Pro集成正在完善中**，预计需要：
- 适配新的LLMProvider接口
- 实现ContentBlock转换
- 完整的错误处理

在此之前，推荐使用**API模式**获得真实Claude回复。🏮
