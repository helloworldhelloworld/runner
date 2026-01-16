# 如何连接Claude API - 配置指南

## 概述

当前应用默认使用**Mock模式**（无需API密钥），要使用真实的Claude API，需要配置Anthropic API密钥。

## 步骤 1: 获取Claude API密钥

### 方法一：通过Anthropic控制台（推荐）

1. **访问Anthropic控制台**
   - 🔗 https://console.anthropic.com/

2. **登录或注册账号**
   - 使用你的Claude会员账号登录
   - 如果没有账号，需要先注册

3. **创建API密钥**
   - 进入 **"API Keys"** 页面
   - 点击 **"Create Key"** 按钮
   - 为密钥命名（例如：soul-comfort-app）
   - 复制生成的API密钥（格式：`sk-ant-...`）
   - ⚠️ **重要**：立即保存密钥，关闭页面后无法再次查看

4. **查看使用额度**
   - 新账号通常有$5免费额度
   - Claude Pro会员可能有更高额度
   - 在 **"Usage"** 页面查看剩余额度

### 方法二：已有API密钥

如果你已经有Anthropic API密钥，可以直接使用。

## 步骤 2: 配置API密钥到应用

你有**三种方式**配置API密钥：

### 方式一：环境变量（推荐，最安全）

**macOS/Linux:**
```bash
# 临时设置（当前终端会话有效）
export ANTHROPIC_API_KEY="sk-ant-your-api-key-here"

# 永久设置（添加到 ~/.zshrc 或 ~/.bash_profile）
echo 'export ANTHROPIC_API_KEY="sk-ant-your-api-key-here"' >> ~/.zshrc
source ~/.zshrc
```

**Windows PowerShell:**
```powershell
# 临时设置
$env:ANTHROPIC_API_KEY="sk-ant-your-api-key-here"

# 永久设置（系统环境变量）
[System.Environment]::SetEnvironmentVariable('ANTHROPIC_API_KEY', 'sk-ant-your-api-key-here', 'User')
```

### 方式二：修改配置文件

编辑 `agent-web/src/main/resources/application.yml`:

```yaml
app:
  mock-mode: false  # 关闭mock模式
  claude:
    api-key: sk-ant-your-api-key-here  # 直接写入（不推荐，容易泄露）
    model: claude-3-5-sonnet-20241022
```

⚠️ **注意**：不推荐将API密钥直接写入配置文件，容易被提交到Git仓库导致泄露。

### 方式三：启动时指定参数

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--app.mock-mode=false --app.claude.api-key=sk-ant-your-api-key-here"
```

## 步骤 3: 重启服务

### 如果服务正在运行，先停止

```bash
# 查找进程
lsof -ti:8080

# 停止进程
lsof -ti:8080 | xargs kill -9
```

### 启动服务

```bash
cd agent-web

# 确保环境变量已设置（方式一）
echo $ANTHROPIC_API_KEY

# 启动服务
mvn spring-boot:run
```

### 验证连接

启动后查看日志，应该看到：

```
✅ 成功：
INFO  c.l.web.config.AgentConfig - Using Claude LLM Provider with model: claude-3-5-sonnet-20241022

❌ 仍在Mock模式：
INFO  c.l.web.config.AgentConfig - Using Mock LLM Provider (no API key configured)
```

## 步骤 4: 测试真实对话

```bash
# 测试心灵引导Agent
curl --noproxy localhost -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "我今天被领导批评了，心里很难受...",
    "sessionId": "test-user-001",
    "soulComfortMode": true
  }'
```

**预期结果（使用真实API）：**
```json
{
  "response": "能感受到你的难过。被批评的感觉确实不好受，尤其是在工作中...",
  "skillsApplied": ["soul-comfort"],
  "metadata": {
    "mode": "soul-comfort",
    "hasMemory": true,
    "hasReflection": true
  }
}
```

## 配置说明

### 当前配置 (application.yml)

```yaml
app:
  mock-mode: true  # true=使用Mock模式, false=使用真实API
  claude:
    api-key: ${ANTHROPIC_API_KEY:}  # 从环境变量读取
    model: claude-3-5-sonnet-20241022  # 使用的模型
```

### 配置逻辑 (AgentConfig.java:40-47)

```java
if (mockMode || claudeApiKey == null || claudeApiKey.isEmpty()) {
    logger.info("Using Mock LLM Provider (no API key configured)");
    return new MockLLMProvider();  // Mock模式
} else {
    logger.info("Using Claude LLM Provider with model: {}", claudeModel);
    return new ClaudeProvider(claudeApiKey, claudeModel);  // 真实API
}
```

## 可用的Claude模型

| 模型名称 | 描述 | 适用场景 |
|---------|------|---------|
| `claude-3-5-sonnet-20241022` | 最新Sonnet (推荐) | 平衡性能和成本 |
| `claude-3-5-sonnet-20240620` | 旧版Sonnet | 稳定版本 |
| `claude-3-opus-20240229` | Opus旗舰模型 | 最强性能，成本较高 |
| `claude-3-haiku-20240307` | Haiku轻量模型 | 快速响应，成本低 |

### 修改模型

在 `application.yml` 中修改：

```yaml
app:
  claude:
    model: claude-3-opus-20240229  # 使用Opus模型
```

## 成本估算

### Claude API价格（以Sonnet为例）

- **Input**: $3 / 1M tokens
- **Output**: $15 / 1M tokens

### 示例对话成本

一次心灵引导对话：
- 输入：约500 tokens（系统提示 + 对话历史 + 用户消息）
- 输出：约200 tokens（Agent回复）

**单次对话成本**: 约 $0.0045（0.5美分）

**100次对话**: 约 $0.45

## 常见问题

### Q1: API密钥无效

**错误信息**:
```
401 Unauthorized: Invalid API Key
```

**解决方法**:
- 确认API密钥格式正确（以 `sk-ant-` 开头）
- 检查是否有多余的空格或换行
- 在Anthropic控制台验证密钥是否激活

### Q2: 超出额度

**错误信息**:
```
429 Too Many Requests: Rate limit exceeded
```

**解决方法**:
- 在控制台查看剩余额度
- 升级到付费计划或等待额度重置
- 降低请求频率

### Q3: 网络连接失败

**错误信息**:
```
Connection timeout
```

**解决方法**:
- 检查网络连接
- 如果在中国大陆，可能需要代理
- 配置代理环境变量：
  ```bash
  export HTTP_PROXY=http://127.0.0.1:7890
  export HTTPS_PROXY=http://127.0.0.1:7890
  ```

### Q4: Mock模式与真实API的区别

| 特性 | Mock模式 | 真实API |
|------|---------|---------|
| 响应内容 | 固定格式的Mock文本 | Claude生成的真实回复 |
| 情绪分析 | 返回用户原文 | 真实情绪识别 |
| 话题提取 | 返回用户原文片段 | 智能话题提取 |
| 心灵引导 | 无法提供真实引导 | 温暖、共情的真实回复 |
| 成本 | 免费 | 按使用量付费 |

## 安全建议

### ✅ 推荐做法

1. **使用环境变量**存储API密钥
2. **不要**将密钥提交到Git仓库
3. 添加 `.env` 到 `.gitignore`
4. 定期轮换API密钥
5. 为不同环境使用不同密钥（开发/生产）

### ❌ 避免的做法

1. 直接将密钥写入代码或配置文件
2. 在日志中打印完整密钥
3. 通过URL传递密钥
4. 将密钥分享给他人
5. 使用根密钥（如果有子密钥选项）

## 快速启动脚本

创建 `start-with-api.sh`:

```bash
#!/bin/bash

# 设置API密钥
export ANTHROPIC_API_KEY="sk-ant-your-api-key-here"

# 进入项目目录
cd agent-web

# 停止现有服务
lsof -ti:8080 | xargs kill -9 2>/dev/null

# 启动服务
echo "🚀 Starting Soul Comfort Agent with Claude API..."
mvn spring-boot:run
```

使用：
```bash
chmod +x start-with-api.sh
./start-with-api.sh
```

## 下一步

✅ 配置完成后，你可以：

1. 在HarmonyOS客户端中测试真实对话
2. 体验完整的心灵引导能力
3. 查看真实的情绪分析和话题提取
4. 使用 `test_soul_comfort.py` 验证功能

---

**需要帮助？** 检查服务日志或查看 [Anthropic文档](https://docs.anthropic.com/)
