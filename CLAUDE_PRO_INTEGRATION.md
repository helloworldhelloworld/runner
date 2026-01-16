# 使用Claude Pro会员替代API - 集成方案

## 核心区别

| 特性 | Claude Pro会员 | Claude API |
|------|---------------|-----------|
| 费用 | $20/月（无限对话） | 按使用量付费 |
| 访问方式 | 网页版 claude.ai | 官方API接口 |
| 适用场景 | 个人使用 | 开发者集成 |
| 限制 | 有使用频率限制 | 按配额计费 |

## 方案对比

### 方案一：使用Session Key（推荐）⭐

通过你的Claude Pro账号的Session Key直接调用claude.ai

**优点**：
- ✅ 使用Pro会员额度，无需额外付费
- ✅ 实现简单，只需Cookie
- ✅ 与网页版使用同一账号

**缺点**：
- ⚠️ 非官方方式，可能违反服务条款
- ⚠️ Session Key可能过期，需要定期更新
- ⚠️ 请求频率有限制

### 方案二：通过Slack集成

使用Claude的Slack应用

**优点**：
- ✅ 官方支持的方式
- ✅ 稳定可靠

**缺点**：
- ❌ 需要Slack工作区
- ❌ 配置复杂
- ❌ 响应格式受限

### 方案三：浏览器自动化

使用Selenium/Puppeteer模拟网页操作

**优点**：
- ✅ 最接近人工操作

**缺点**：
- ❌ 性能开销大
- ❌ 维护成本高
- ❌ 容易被检测

## 推荐实现：Session Key方案

### 步骤1：获取Session Key

1. **登录Claude网页版**
   - 访问 https://claude.ai
   - 使用你的Pro账号登录

2. **打开浏览器开发者工具**
   - Chrome/Edge: 按 `F12` 或 `Cmd+Option+I` (Mac)
   - Firefox: 按 `F12` 或 `Cmd+Option+I` (Mac)

3. **找到Session Key**

   **方法A：从Cookie中获取**
   - 点击 `Application` (应用程序) 标签
   - 左侧选择 `Cookies` → `https://claude.ai`
   - 查找名为 `sessionKey` 或 `__Secure-SessionKey` 的Cookie
   - 复制它的值（通常是一个长字符串）

   **方法B：从请求中获取**
   - 点击 `Network` (网络) 标签
   - 在claude.ai中发送一条消息
   - 在网络请求中找到 `api/organizations/.../chat_conversations`
   - 查看 `Request Headers` → `Cookie`
   - 复制 `sessionKey=...` 的值

4. **保存Session Key**
   - 格式类似：`sk-ant-sid01-...` 或 `sess-...`

### 步骤2：配置应用使用Session Key

#### 方法A：使用环境变量（推荐）

```bash
# 设置Session Key
export CLAUDE_SESSION_KEY="your-session-key-here"

# 设置Provider类型为pro
export PROVIDER_TYPE=pro

# 启动服务
cd agent-web
./start-with-pro.sh
```

#### 方法B：使用.env文件

```bash
cd agent-web

# 复制配置模板
cp .env.example .env

# 编辑.env文件
nano .env
```

在.env中填入：
```bash
PROVIDER_TYPE=pro
CLAUDE_SESSION_KEY=your-session-key-here
```

然后加载环境变量：
```bash
source .env
./start-with-pro.sh
```

### 步骤3：验证连接

启动后查看日志，应该看到：

```
✅ 成功：
INFO  c.l.web.config.AgentConfig - Using Claude Pro Provider (session-based, using Pro member account)
INFO  c.l.k.l.c.ClaudeProProvider - Claude Pro Provider initialized (using session key)
INFO  c.l.k.l.c.ClaudeProProvider - Auto-fetched organization ID: xxx-xxx-xxx

❌ Session Key无效：
WARN  c.l.web.config.AgentConfig - Claude Pro mode selected but no session key provided, falling back to Mock
```

### 步骤4：测试对话

```bash
# 清除代理设置
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY

# 测试心灵引导
curl --noproxy localhost -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "我今天压力好大...",
    "sessionId": "test-pro-001",
    "soulComfortMode": true
  }'
```

**预期结果（使用Pro会员）：**
```json
{
  "response": "能感受到你的压力。这种感觉就像背着很重的行李，对吗？想聊聊是什么让你感觉这么沉重吗？",
  "skillsApplied": ["soul-comfort"],
  "metadata": {
    "mode": "soul-comfort",
    "hasMemory": true,
    "hasReflection": true,
    "recentEmotions": ["焦虑"],
    "importantTopics": ["工作压力"]
  }
}
```

## 实现原理

### 技术架构

```
用户请求
    ↓
SoulComfortChatService
    ↓
SoulComfortAgent
    ↓
ClaudeProProvider  ← 使用Session Key
    ↓
claude.ai API (非官方)
    ↓
Claude Pro 账号额度
```

### ClaudeProProvider关键功能

1. **自动获取Organization ID**
   ```java
   GET https://claude.ai/api/organizations
   Headers: Cookie: sessionKey=xxx
   ```

2. **创建对话**
   ```java
   POST https://claude.ai/api/organizations/{orgId}/chat_conversations
   Body: {"name": "Soul Comfort Chat", "uuid": "..."}
   ```

3. **发送消息**
   ```java
   POST https://claude.ai/api/organizations/{orgId}/chat_conversations/{convId}
   Body: {"prompt": "用户消息", "model": "claude-3-5-sonnet-20241022"}
   Headers:
     - Cookie: sessionKey=xxx
     - Accept: text/event-stream
   ```

4. **解析SSE响应**
   ```
   data: {"completion": "能感受到你的", "stop_reason": null}
   data: {"completion": "压力。这种", "stop_reason": null}
   data: {"completion": "感觉就像...", "stop_reason": "end_turn"}
   data: [DONE]
   ```

## 配置对比

### Mock模式 vs API模式 vs Pro模式

| 特性 | Mock模式 | API模式 | Pro模式 |
|------|---------|---------|---------|
| **费用** | 免费 | 按量付费 | 包含在Pro会员($20/月) |
| **配置** | 无需配置 | 需要API Key | 需要Session Key |
| **响应质量** | 固定Mock文本 | Claude真实回复 | Claude真实回复 |
| **稳定性** | 100% | 高 | 中（Key可能过期） |
| **官方支持** | N/A | ✅ | ❌ 非官方 |
| **频率限制** | 无 | 按配额 | Pro会员限制 |

### 配置文件对比

**application.yml:**
```yaml
app:
  provider-type: mock  # 或 api 或 pro
  claude:
    # API模式
    api-key: ${ANTHROPIC_API_KEY:}
    model: claude-3-5-sonnet-20241022

    # Pro模式
    session-key: ${CLAUDE_SESSION_KEY:}
    organization-id: ${CLAUDE_ORG_ID:}
```

**环境变量：**
```bash
# Mock模式（默认）
# 无需设置

# API模式
export PROVIDER_TYPE=api
export ANTHROPIC_API_KEY=sk-ant-xxx

# Pro模式
export PROVIDER_TYPE=pro
export CLAUDE_SESSION_KEY=sess-xxx
```

## 常见问题

### Q1: Session Key在哪里？

**Chrome/Edge:**
1. 打开 https://claude.ai
2. 按 `F12` 或 `Cmd+Option+I` (Mac)
3. 点击 **Application** 标签
4. 左侧：**Cookies** → **https://claude.ai**
5. 查找：`sessionKey` 或 `__Secure-SessionKey`
6. 复制值

**Firefox:**
1. 打开 https://claude.ai
2. 按 `F12`
3. 点击 **Storage** 标签
4. **Cookies** → **https://claude.ai**
5. 查找并复制 `sessionKey`

**Safari:**
1. 开发菜单 → 显示Web检查器
2. 存储 → Cookies
3. 查找 `sessionKey`

### Q2: Session Key会过期吗？

**是的**，Session Key通常：
- 90天自动过期（如果不使用）
- 登出后立即失效
- 更换浏览器/设备后失效

**解决方法**：
- 定期（1-2个月）更新Session Key
- 出现401错误时重新获取
- 在应用中添加自动检测和提醒

### Q3: 使用Pro模式会被封号吗？

**风险说明**：
- ⚠️ 这是非官方使用方式
- 可能违反Claude服务条款
- Anthropic可能会限制或封禁账号

**降低风险建议**：
1. 适度使用，不要频繁请求
2. 遵守Pro会员的使用限制
3. 不要用于商业用途
4. 考虑购买官方API（更稳定合规）

### Q4: 无法获取Organization ID

**错误信息**：
```
Failed to auto-fetch organization ID
```

**解决方法**：

1. **手动获取Organization ID**：
   ```bash
   curl https://claude.ai/api/organizations \
     -H "Cookie: sessionKey=your-session-key"
   ```

2. **在代码中设置**：
   ```bash
   export CLAUDE_ORG_ID=your-org-id
   ```

3. **检查Session Key**：
   - 确保完整复制（通常很长）
   - 没有多余空格或换行
   - 尚未过期

### Q5: Pro模式与API模式如何选择？

| 场景 | 推荐方式 |
|------|---------|
| 个人学习/测试 | **Pro模式** |
| 商业应用 | **API模式** |
| 需要高稳定性 | **API模式** |
| 预算有限 | **Pro模式** |
| 大量使用 | **API模式** |

## 安全建议

### ⚠️ 重要警告

1. **不要分享Session Key**
   - Session Key等同于账号密码
   - 他人获取后可以访问你的对话历史
   - 可能导致账号被盗用

2. **不要提交到Git**
   - 已添加.env到.gitignore
   - 检查: `git status` 不应显示.env
   - 使用环境变量而非硬编码

3. **定期更换Key**
   - 建议每2-3个月更换
   - 发现异常立即更换
   - 使用后及时清理日志

### ✅ 最佳实践

```bash
# 1. 设置环境变量（仅当前终端）
export CLAUDE_SESSION_KEY="xxx"

# 2. 启动服务
./start-with-pro.sh

# 3. 使用完毕后清理
unset CLAUDE_SESSION_KEY

# 4. 关闭终端时自动清理
trap 'unset CLAUDE_SESSION_KEY' EXIT
```

## 快速切换模式

创建快捷脚本 `switch-mode.sh`:

```bash
#!/bin/bash

echo "选择LLM Provider模式："
echo "1. Mock模式（免费，无真实AI）"
echo "2. API模式（付费，官方API）"
echo "3. Pro模式（使用Pro会员）"
read -p "请选择 (1-3): " choice

case $choice in
  1)
    export PROVIDER_TYPE=mock
    echo "✅ 已切换到Mock模式"
    ;;
  2)
    read -p "请输入API Key: " key
    export PROVIDER_TYPE=api
    export ANTHROPIC_API_KEY=$key
    echo "✅ 已切换到API模式"
    ;;
  3)
    read -p "请输入Session Key: " key
    export PROVIDER_TYPE=pro
    export CLAUDE_SESSION_KEY=$key
    echo "✅ 已切换到Pro模式"
    ;;
  *)
    echo "❌ 无效选择"
    exit 1
    ;;
esac

# 重启服务
./start-with-pro.sh
```

## 总结

### 使用Claude Pro会员的步骤

1. **获取Session Key**（5分钟）
   - 登录claude.ai
   - F12 → Application → Cookies
   - 复制sessionKey

2. **配置环境变量**（1分钟）
   ```bash
   export PROVIDER_TYPE=pro
   export CLAUDE_SESSION_KEY="your-key"
   ```

3. **启动服务**（30秒）
   ```bash
   cd agent-web
   ./start-with-pro.sh
   ```

4. **开始使用**
   - 所有API请求使用Pro会员额度
   - 无需额外付费
   - 享受真实Claude回复

### 推荐配置

- ✅ 个人开发/学习：使用**Pro模式**
- ✅ 生产环境/商业：使用**API模式**
- ✅ 测试/演示：使用**Mock模式**

---

**需要帮助？** 检查日志或查看 [非官方Claude API讨论](https://github.com/anthropics/anthropic-sdk-python/issues)
