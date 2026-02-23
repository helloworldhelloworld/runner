# 🎤 Azure Speech Services 配置指南

## 📋 概述

Azure Speech Services 提供高质量的中文语音识别和合成，**每月有免费额度**：
- 🎙️ STT（语音识别）：5 小时/月
- 🔊 TTS（语音合成）：500 万字符/月

非常适合个人学习和中小型项目！

---

## 🚀 快速配置（10 分钟）

### 步骤 1：创建 Azure 账户

#### 1.1 访问 Azure

打开浏览器访问：https://portal.azure.com

#### 1.2 注册账户

如果还没有账户：
1. 点击右上角 "免费账户"
2. 使用 Microsoft 账户登录（或创建新账户）
3. 填写基本信息
4. **需要信用卡验证**（但不会扣费）
5. 获得 **$200 免费额度**（30 天有效）

**注意**：
- 信用卡仅用于验证，不会自动扣费
- 免费层服务不会产生费用
- 超出免费额度后会提示升级

---

### 步骤 2：创建语音服务资源

#### 2.1 搜索语音服务

1. 登录后，点击左上角 **"创建资源"**（+ Create a resource）
2. 在搜索框输入：**"Speech"** 或 **"语音"**
3. 选择 **"语音服务"**（Speech Services）
4. 点击 **"创建"**（Create）

![搜索语音服务](https://docs.microsoft.com/azure/cognitive-services/speech-service/media/index/speech-services.png)

#### 2.2 配置资源

填写以下信息：

**基本信息（Basics）**：

| 字段 | 值 | 说明 |
|------|-----|------|
| **订阅** | 选择你的订阅 | 通常是 "免费试用" |
| **资源组** | 新建或选择现有 | 建议新建：`rg-soul-comfort` |
| **区域** | **East Asia（东亚）** | 推荐！延迟最低 |
| **名称** | `speech-soul-comfort` | 自定义，需全局唯一 |
| **定价层** | **Free F0** | **重要**：选择免费层 |

**关键选择**：
- ✅ **区域选择 East Asia（东亚/香港）**：延迟低，中文支持好
- ✅ **定价层选择 Free F0**：免费额度，够用

#### 2.3 审阅并创建

1. 点击 **"审阅 + 创建"**（Review + create）
2. 检查配置无误
3. 点击 **"创建"**（Create）
4. 等待部署完成（约 1-2 分钟）

---

### 步骤 3：获取 API Key 和 Region

#### 3.1 进入资源

部署完成后：
1. 点击 **"转到资源"**（Go to resource）
2. 或在 Azure Portal 主页找到刚创建的语音服务

#### 3.2 查看密钥

在左侧菜单找到：
- **"密钥和终结点"**（Keys and Endpoint）
- 或 **"资源管理 → 密钥和终结点"**

你会看到：

```
密钥 1（KEY 1）:  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
密钥 2（KEY 2）:  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
位置/区域:        eastasia
终结点:          https://eastasia.api.cognitive.microsoft.com/sts/v1.0/issuetoken
```

#### 3.3 复制信息

需要复制的信息：
- ✅ **密钥 1** 或 **密钥 2**（任选其一）
- ✅ **位置/区域**（Location/Region）

**示例**：
```bash
AZURE_SPEECH_KEY=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
AZURE_SPEECH_REGION=eastasia
```

---

### 步骤 4：配置到项目

#### 4.1 编辑配置文件

```bash
cd agent-web

# 创建或编辑 .env 文件
vi .env
```

#### 4.2 添加配置

```bash
# ========================================
# LLM 配置（如果已有 OpenRouter）
# ========================================
PROVIDER_TYPE=openrouter
OPENROUTER_API_KEY=sk-or-v1-你的OpenRouter-Key

# ========================================
# Azure Speech 配置
# ========================================
SPEECH_PROVIDER=azure
AZURE_SPEECH_KEY=你的Azure-Key（从步骤3复制）
AZURE_SPEECH_REGION=eastasia

# 可选：选择音色（默认：晓晓）
AZURE_SPEECH_VOICE=zh-CN-XiaoxiaoNeural
```

#### 4.3 音色选择（可选）

Azure 提供多种中文音色：

| 音色代码 | 描述 | 适用场景 |
|---------|------|----------|
| `zh-CN-XiaoxiaoNeural` | 晓晓（女声）| 温柔、亲切，**推荐** |
| `zh-CN-YunxiNeural` | 云希（男声）| 沉稳、可靠 |
| `zh-CN-YunyangNeural` | 云扬（男声）| 专业、新闻播报 |
| `zh-CN-XiaoyiNeural` | 晓伊（女声）| 活泼、年轻 |
| `zh-CN-YunjianNeural` | 云健（男声）| 运动、健康 |
| `zh-CN-XiaochenNeural` | 晓辰（女声）| 客服、助手 |

**试听音色**：
https://speech.microsoft.com/portal/voicegallery

---

### 步骤 5：重启服务

#### 5.1 加载配置

```bash
cd agent-web
source .env
```

#### 5.2 停止旧服务

```bash
pkill -f 'java.*agent-web'
```

等待 2-3 秒确保进程已停止。

#### 5.3 启动服务

```bash
./start-with-env.sh
```

#### 5.4 查看日志

启动时应该看到：

```
✅ INFO  AgentConfig - Using Azure Speech Services (region: eastasia, voice: zh-CN-XiaoxiaoNeural)
```

如果看到这个，说明配置成功！

---

## ✅ 验证配置

### 方法 1：运行诊断脚本

```bash
cd agent-web
./check-keys.sh
```

应该显示：

```
✅ AZURE_SPEECH_KEY: 已设置
   AZURE_SPEECH_REGION: eastasia
   状态: ✅ Azure Speech 已配置且已启用
```

### 方法 2：测试语音功能

1. 打开浏览器访问：http://localhost:8080
2. 点击 🎤 按钮
3. 说一句话
4. 再次点击停止
5. 应该能看到：
   - ✅ 识别的文字
   - ✅ AI 的回复
   - ✅ 听到语音播放

### 方法 3：查看 Azure Portal 使用情况

1. 访问 https://portal.azure.com
2. 进入你的语音服务资源
3. 左侧菜单 → **"监视" → "指标"**
4. 查看 API 调用次数

---

## 🎨 高级配置：情感语音（SSML）

Azure Speech 支持 SSML（语音合成标记语言），可以精确控制：
- 语速
- 音调
- 情感
- 停顿

### 代码实现

项目中已经实现了情感映射：

```java
// agent-kernel/src/main/java/com/lightweightai/kernel/speech/AzureSpeechProvider.java

private String emotionToAzureStyle(String emotion) {
    switch (emotion) {
        case "开心":
        case "鼓励":
            return "cheerful";      // 欢快
        case "悲伤":
        case "沮丧":
            return "sad";          // 悲伤
        case "平静":
        case "温柔":
            return "gentle";       // 温柔
        case "关心":
            return "empathetic";   // 同理心
        default:
            return "calm";         // 平静
    }
}
```

### SSML 示例

```xml
<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="zh-CN">
    <voice name="zh-CN-XiaoxiaoNeural">
        <prosody rate="0%" pitch="0%">
            <mstts:express-as style="gentle">
                能感受到你的情绪，愿意听你倾诉。
            </mstts:express-as>
        </prosody>
    </voice>
</speak>
```

---

## 💰 费用说明

### 免费层（F0）限额

| 服务 | 免费额度/月 | 超出后价格 |
|------|------------|-----------|
| **STT（标准）** | 5 小时 | $1 / 小时 |
| **TTS（神经网络）** | 500 万字符 | $16 / 100 万字符 |

### 使用量估算

**场景**：每天 50 次语音对话

| 项目 | 每次 | 每天 | 每月 | 是否超限 |
|------|------|------|------|---------|
| STT | 30 秒 | 25 分钟 | 12.5 小时 | ⚠️ 会超出 |
| TTS | 100 字 | 5000 字 | 15 万字符 | ✅ 不会 |

**结论**：
- TTS：**完全免费**（15 万 < 500 万）
- STT：**前 10 天免费**，之后约 $7.5/月

**优化建议**：
1. 录音时长控制在 10-15 秒
2. 使用缓存避免重复识别
3. 考虑使用 OpenAI Whisper 作为备选

---

## 🔧 故障排查

### 问题 1：创建资源失败

**错误**：`配额超出限制`

**原因**：免费订阅限制

**解决**：
1. 每个订阅只能创建 1 个免费语音服务
2. 如果已有免费资源，删除后重试
3. 或创建新的 Azure 账户

---

### 问题 2：401 Unauthorized

**错误信息**：
```
Azure Speech API 调用失败: 401 Unauthorized
```

**检查**：
1. API Key 是否正确复制（没有多余空格）
2. Region 是否匹配（eastasia vs chinaeast）
3. 资源是否已启用

**测试 API Key**：
```bash
curl -v -X POST \
  "https://eastasia.tts.speech.microsoft.com/cognitiveservices/v1" \
  -H "Ocp-Apim-Subscription-Key: 你的Key" \
  -H "Content-Type: application/ssml+xml" \
  -d '<speak version="1.0" xml:lang="zh-CN"><voice name="zh-CN-XiaoxiaoNeural">测试</voice></speak>'
```

---

### 问题 3：语音质量差

**可能原因**：
1. 网络延迟高
2. 音色选择不当
3. 音频参数设置

**优化**：
1. 确认 Region 为 `eastasia`（离中国最近）
2. 尝试不同音色
3. 调整 SSML 参数（语速、音调）

---

### 问题 4：超出免费额度

**检查使用量**：
1. Azure Portal → 你的语音服务
2. "监视" → "指标"
3. 查看 API 调用统计

**控制成本**：
1. 添加预算警报
2. 设置支出限制
3. 优化录音时长

---

## 📊 Azure vs OpenAI 对比

| 维度 | Azure Speech | OpenAI Speech |
|------|-------------|---------------|
| **免费额度** | ✅ 5h STT + 500万字 TTS/月 | ❌ 无 |
| **STT 质量** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **TTS 质量** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **中文支持** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **情感控制** | ⭐⭐⭐⭐⭐ (SSML) | ⭐⭐⭐ |
| **音色数量** | 数十种中文音色 | 6 种英文音色 |
| **延迟** | ~500-800ms | ~300-500ms |
| **适用场景** | 中文、学习、预算有限 | 英文、高质量、不差钱 |

**推荐**：
- 🎯 **学习/测试**：Azure（免费）
- 🎯 **中文场景**：Azure（情感控制好）
- 🎯 **英文/高端**：OpenAI（质量最佳）

---

## 🎓 常见问题

### Q1: 免费额度用完后会自动扣费吗？

**A**: 不会！免费层（F0）有硬性限制，超出后：
- API 返回 403 错误
- 需要手动升级到付费层才能继续使用

### Q2: 可以同时使用多个 Azure 账户吗？

**A**: 可以，但：
- 每个账户需要不同的邮箱
- 每个账户有 $200 免费额度（30 天）
- 管理多个账户比较麻烦

### Q3: Region 选哪个最好？

**A**: 推荐顺序：
1. **East Asia（东亚/香港）**：延迟最低，中文支持最好
2. Southeast Asia（东南亚/新加坡）
3. Japan East（日本东部）

### Q4: 如何切换音色？

**A**: 修改配置文件：
```bash
# 原来
AZURE_SPEECH_VOICE=zh-CN-XiaoxiaoNeural

# 改为云希（男声）
AZURE_SPEECH_VOICE=zh-CN-YunxiNeural
```

然后重启服务。

---

## 📖 相关文档

- **Azure 官方文档**：https://docs.microsoft.com/azure/cognitive-services/speech-service/
- **音色列表**：https://speech.microsoft.com/portal/voicegallery
- **SSML 参考**：https://docs.microsoft.com/azure/cognitive-services/speech-service/speech-synthesis-markup
- **定价详情**：https://azure.microsoft.com/pricing/details/cognitive-services/speech-services/

---

## ✅ 配置检查清单

完成后检查：

- [ ] Azure 账户已创建
- [ ] 语音服务资源已创建（Free F0）
- [ ] API Key 和 Region 已复制
- [ ] .env 文件已配置
- [ ] 服务已重启
- [ ] 启动日志显示 "Using Azure Speech Services"
- [ ] 诊断脚本显示 ✅
- [ ] 语音功能测试成功

全部打勾即配置完成！🎉

---

**最后更新**: 2026-01-24
**维护**: 解忧杂货铺项目组
