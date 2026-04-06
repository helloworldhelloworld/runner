# 自动获取Claude Session Key 🔑

## 功能说明

**自动从浏览器提取Session Key**，无需手动复制！

支持的浏览器：
- ✅ Google Chrome
- ✅ Microsoft Edge
- ✅ Firefox
- ✅ Chromium系浏览器

## 快速使用

### 方法一：一键获取（推荐）⭐

```bash
cd agent-web

# 运行自动获取脚本
python3 get-session-key.py
```

脚本会：
1. 自动搜索所有浏览器的Cookie
2. 提取claude.ai的sessionKey
3. 询问是否保存到.env文件
4. 自动配置PROVIDER_TYPE=pro

### 方法二：手动验证

如果自动获取失败，可以手动验证：

```bash
# 1. 确保已登录claude.ai
# 2. 关闭浏览器
# 3. 运行脚本
python3 get-session-key.py
```

## 使用流程

### 完整步骤

```bash
# 1. 登录Claude
#    在浏览器中访问 https://claude.ai 并登录

# 2. 自动获取Session Key
cd agent-web
python3 get-session-key.py

# 输出示例：
# ✅ Session Key 获取成功!
# 浏览器: Chrome
# Session Key: sk-ant-sid01-xxxxx...
# 是否保存到 .env 文件？(y/n) [y]:

# 3. 输入 y 保存

# 4. 加载环境变量
source .env

# 5. 启动服务
./start-with-pro.sh

# 6. 测试对话
python3 test_soul_comfort.py
```

## 工作原理

### 技术实现

1. **定位Cookie文件**
   ```python
   # macOS Chrome
   ~/Library/Application Support/Google/Chrome/Default/Cookies

   # macOS Edge
   ~/Library/Application Support/Microsoft Edge/Default/Cookies

   # macOS Firefox
   ~/Library/Application Support/Firefox/Profiles/*/cookies.sqlite
   ```

2. **读取SQLite数据库**
   ```sql
   SELECT name, value, host_key
   FROM cookies
   WHERE host_key LIKE '%claude.ai%'
   AND (name = 'sessionKey' OR name = '__Secure-sessionKey')
   ```

3. **提取并验证**
   - 检查Session Key长度（通常>20字符）
   - 自动选择最新的有效Key

### 安全性

✅ **安全措施**：
- 只读取本地Cookie，不发送到任何服务器
- 使用临时文件副本，不修改浏览器数据
- .env文件已在.gitignore中，不会被提交

⚠️ **注意事项**：
- Session Key等同于账号密码，请妥善保管
- 不要分享.env文件
- 定期更新Session Key

## 故障排除

### 问题1：未找到Session Key

**可能原因**：
- 未登录claude.ai
- 浏览器正在运行（Cookie被锁定）
- 使用了不支持的浏览器

**解决方法**：
```bash
# 1. 确保已登录
# 访问 https://claude.ai，确认已登录

# 2. 关闭浏览器
# 完全退出Chrome/Edge/Firefox

# 3. 重新运行
python3 get-session-key.py
```

### 问题2：无法读取Cookie文件

**错误信息**：
```
database is locked
```

**解决方法**：
```bash
# 确保浏览器已完全关闭
ps aux | grep -i chrome
ps aux | grep -i edge
ps aux | grep -i firefox

# 如果还在运行，强制关闭
killall "Google Chrome"
killall "Microsoft Edge"
killall firefox

# 然后重新运行脚本
python3 get-session-key.py
```

### 问题3：支持其他浏览器

**当前支持**：Chrome、Edge、Firefox

**其他浏览器**（Arc、Brave、Vivaldi等）：
```bash
# 这些浏览器通常基于Chromium
# 可以尝试查找它们的Cookie路径

# Arc (macOS)
~/Library/Application Support/Arc/User Data/Default/Cookies

# Brave (macOS)
~/Library/Application Support/BraveSoftware/Brave-Browser/Default/Cookies

# 手动指定路径后使用相同的提取逻辑
```

## 手动获取（备用方案）

如果自动获取失败，可以手动获取：

### Chrome/Edge

1. 访问 https://claude.ai
2. 按 `F12` 打开开发者工具
3. 点击 **Application** 标签
4. 左侧：**Cookies** → **https://claude.ai**
5. 查找 `sessionKey` 并复制值
6. 手动设置：
   ```bash
   export CLAUDE_SESSION_KEY="复制的值"
   ```

### Firefox

1. 访问 https://claude.ai
2. 按 `F12` 打开开发者工具
3. 点击 **Storage** 标签
4. **Cookies** → **https://claude.ai**
5. 查找 `sessionKey` 并复制值

## 使用示例

### 示例1：首次使用

```bash
$ cd agent-web
$ python3 get-session-key.py

============================================================
Claude Session Key 自动获取工具
============================================================

⚠️  提示：如果浏览器正在运行，可能无法读取Cookie
   建议：先关闭浏览器，或者忽略此警告继续

🔍 正在搜索Claude Session Key...

检查 Chrome... ✓ 找到Cookie文件
✅ 成功从 Chrome 提取Session Key!

============================================================
✅ Session Key 获取成功!
============================================================

浏览器: Chrome
Session Key: sk-ant-sid01-xxxxx...

是否保存到 .env 文件？(y/n) [y]: y
✅ 已保存到: /path/to/.env

使用方法：
  1. source .env
  2. ./start-with-pro.sh

============================================================
🏮 现在可以启动解忧杂货铺了！
============================================================
```

### 示例2：更新过期的Key

```bash
# Session Key过期时，重新获取
$ python3 get-session-key.py

# 会自动更新.env文件中的旧Key
✅ 已保存到: /path/to/.env

# 重新加载环境变量
$ source .env

# 重启服务
$ ./start-with-pro.sh
```

## 进阶用法

### 定期自动更新

创建定时任务自动更新Session Key：

```bash
# 创建更新脚本
cat > update-session-key.sh <<'EOF'
#!/bin/bash
cd /path/to/agent-web
python3 get-session-key.py --auto-yes
source .env
./restart-service.sh
EOF

chmod +x update-session-key.sh

# 添加到crontab（每天凌晨2点更新）
crontab -e
# 添加：
# 0 2 * * * /path/to/update-session-key.sh
```

### 多浏览器环境

如果同时使用多个浏览器：

```bash
# 脚本会自动选择第一个找到的有效Session Key
# 优先级：Chrome > Edge > Firefox

# 如果想指定浏览器，可以修改脚本中的顺序
```

### 与CI/CD集成

```yaml
# GitHub Actions示例
- name: Get Claude Session Key
  run: |
    cd agent-web
    python3 get-session-key.py --auto-yes
    source .env

- name: Run Tests
  run: |
    mvn spring-boot:run &
    sleep 10
    python3 test_soul_comfort.py
```

## 安全最佳实践

### ✅ 推荐做法

1. **使用环境变量**
   ```bash
   # 不要硬编码Session Key
   source .env
   ./start-with-pro.sh
   ```

2. **定期更新**
   ```bash
   # 每1-2个月更新一次
   python3 get-session-key.py
   ```

3. **保护.env文件**
   ```bash
   # 检查.gitignore
   cat ../.gitignore | grep .env

   # 设置文件权限
   chmod 600 .env
   ```

### ❌ 避免的做法

1. 不要将Session Key提交到Git
2. 不要在日志中打印完整Key
3. 不要分享.env文件
4. 不要在公共网络使用

## 总结

### 优势

✅ **完全自动化** - 无需手动复制粘贴
✅ **支持多浏览器** - Chrome、Edge、Firefox
✅ **安全可靠** - 只读取本地文件，不联网
✅ **易于使用** - 一条命令完成

### 使用流程（3步）

```bash
# 1. 自动获取
python3 get-session-key.py

# 2. 加载配置
source .env

# 3. 启动服务
./start-with-pro.sh
```

### 对比手动方式

| 步骤 | 手动方式 | 自动方式 |
|------|---------|---------|
| 1 | 打开浏览器开发者工具 | 运行脚本 |
| 2 | 找到Cookies标签 | 自动完成 |
| 3 | 查找sessionKey | 自动完成 |
| 4 | 复制值 | 自动完成 |
| 5 | 手动设置环境变量 | 自动保存 |
| **时间** | ~2分钟 | ~10秒 |

---

**现在可以一键获取Session Key，使用Claude Pro会员享受无限对话！** 🏮
