# 心灵引导Agent - 技术文档 🏮

## 概述

**解忧杂货铺心灵引导Agent**是一个具备记忆和反思能力的AI助手，专门为情感支持和心理疏导场景设计。

### 核心能力

✅ **记忆能力** - 记住用户的对话历史和个人信息
✅ **反思能力** - 深度理解用户的情绪和需求
✅ **共情能力** - 提供温暖、包容的回应
✅ **引导能力** - 帮助用户理清思路，找到答案

## 架构设计

### 核心组件

```
SoulComfortAgent (心灵引导Agent)
    ├── ConversationMemory (会话记忆)
    │   ├── 短期记忆 (对话历史)
    │   └── 长期记忆 (用户信息)
    ├── ReflectionService (反思服务)
    │   ├── 情绪分析
    │   ├── 话题提取
    │   └── 反思性回应生成
    └── LLMProvider (语言模型)
        └── Claude API
```

### 文件结构

```
agent-web/
└── src/main/java/com/lightweightai/web/
    ├── agent/
    │   ├── SoulComfortAgent.java         # 核心Agent
    │   └── ReflectionService.java        # 反思服务
    ├── service/
    │   └── SoulComfortChatService.java   # Web服务集成
    ├── controller/
    │   └── ChatController.java           # API端点
    └── model/
        └── ChatRequest.java              # 请求模型

agent-kernel/
└── src/main/java/com/lightweightai/kernel/
    └── memory/
        ├── ConversationMemory.java       # 会话记忆管理
        └── UserMemory.java               # 用户长期记忆

agent-web/example-skills/
└── soul-comfort/SKILL.md                 # 心灵引导Skill定义
```

## 功能详解

### 1. 记忆系统

#### 短期记忆（会话历史）

```java
ConversationMemory memory = new ConversationMemory();

// 添加消息到历史
memory.addMessage(sessionId, message);

// 获取最近N条消息
List<ConversationMessage> recent = memory.getRecentMessages(sessionId, 10);

// 自动管理历史大小（默认保留50条）
```

**特性**:
- 自动限制历史大小
- 保留系统消息 + 最近的用户对话
- 支持多会话隔离

#### 长期记忆（用户信息）

```java
UserMemory userMemory = memory.getUserMemory(sessionId);

// 存储基本信息
userMemory.put("userName", "小明");
userMemory.put("mainConcerns", "工作压力");

// 记录情绪
userMemory.addEmotionRecord("焦虑", "工作deadline临近");

// 记录重要话题
userMemory.addImportantTopic("职业发展");
```

**存储内容**:
- 基本信息（用户名、偏好等）
- 情绪历史（最近20条）
- 重要话题（最多10个）
- 首次见面和最后互动时间

### 2. 反思能力

#### 情绪分析

```java
ReflectionService reflection = new ReflectionService(llmProvider);

// 分析用户情绪
String emotion = reflection.analyzeEmotion("我今天压力好大...");
// 返回: "焦虑"
```

**支持的情绪词**:
- 焦虑、悲伤、快乐、困惑、愤怒、平静
- 沮丧、兴奋、紧张、轻松、失落、满足
- 等等...

#### 话题识别

```java
// 识别对话中的重要话题
List<String> topics = reflection.identifyImportantTopics(conversation);
// 返回: ["工作压力", "人际关系", "自我成长"]
```

#### 对话要点提取

```java
// 总结最近的对话
String summary = reflection.extractKeyPoints(recentMessages);
// 返回: "用户主要关心工作压力问题，情绪状态从焦虑逐渐转向平静..."
```

### 3. 心灵引导

#### 核心系统提示

Agent使用专门设计的系统提示，包含：

```
- 使命：用心倾听、温暖陪伴、帮助理清思路
- 风格：温和真诚、善于提问、不说教
- 原则：不轻视感受、引导而非指导、保持希望
- 形象：像夜晚的灯笼，不刺眼但能照亮前行的路 🏮
```

#### 对话流程

```
用户输入
   ↓
情绪分析 → 记录情绪历史
   ↓
话题识别 → 更新关注话题
   ↓
构建上下文（系统提示 + 用户记忆 + 对话历史）
   ↓
LLM生成回应
   ↓
保存到记忆
   ↓
返回给用户
```

## API使用

### 启用心灵引导模式

```bash
POST /api/chat
Content-Type: application/json

{
  "message": "我今天压力好大...",
  "sessionId": "user-123",
  "soulComfortMode": true
}
```

**响应示例**:
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

### 获取会话摘要

```bash
GET /api/session/{sessionId}/summary
```

**响应示例**:
```json
{
  "summary": "用户主要关心工作压力和人际关系...",
  "recentEmotions": [
    {
      "emotion": "焦虑",
      "context": "工作deadline临近",
      "timestamp": "2024-01-10T10:30:00"
    }
  ],
  "importantTopics": ["工作压力", "人际关系"],
  "basicInfo": {
    "userName": "小明"
  },
  "firstMeet": "2024-01-10T09:00:00",
  "lastInteraction": "2024-01-10T10:35:00"
}
```

### 清空会话

```bash
DELETE /api/session/{sessionId}
```

## 对话示例

### 示例 1: 情感支持

**用户**: 我今天被领导批评了，心里很难受...

**Agent分析**:
- 情绪：悲伤
- 话题：工作、人际关系

**Agent回应**:
"被批评的感觉确实不好受。能理解你现在的心情。想聊聊发生了什么吗？或者，你现在最需要的是什么？"

**特点**:
- ✅ 接纳情绪（"确实不好受"）
- ✅ 表达理解（"能理解你"）
- ✅ 开放式提问（引导表达）
- ✅ 简洁而温暖

### 示例 2: 引导思考

**用户**: 我不知道该不该换工作...

**Agent分析**:
- 情绪：困惑
- 话题：职业选择

**Agent回应**:
"这个选择确实不容易。如果可以抛开所有顾虑，你内心最想要的是什么样的工作状态呢？"

**特点**:
- ✅ 认可困难（"确实不容易"）
- ✅ 启发性提问（帮助澄清真实需求）
- ✅ 引导而非指导
- ✅ 尊重选择权

### 示例 3: 持续陪伴（第二次对话）

**上次对话**: 用户表达工作压力
**本次用户**: 我按你说的试了试，感觉好多了

**Agent（记住上次的情绪和话题）**:
"真为你高兴！看到你的变化，说明你已经在调整了。这段时间工作压力有减轻一些吗？"

**特点**:
- ✅ 利用记忆（知道上次的话题）
- ✅ 肯定成长（"你已经在调整"）
- ✅ 持续关注（询问后续情况）
- ✅ 建立连续性

## 配置说明

### 记忆大小配置

```java
// 自定义历史大小
ConversationMemory memory = new ConversationMemory(100); // 保留100条消息
```

### 温度参数

```java
// Agent默认温度: 0.8 (稍高，更人性化)
LLMOptions options = LLMOptions.builder()
    .temperature(0.8)
    .maxTokens(500)
    .build();
```

### Skill加载

心灵引导Skill自动从 `example-skills/soul-comfort.skill` 加载。

## 最佳实践

### 1. 会话ID管理

**推荐做法**:
```javascript
// 前端生成唯一会话ID
const sessionId = `user-${userId}-${Date.now()}`;

// 在本地存储，保持会话连续性
localStorage.setItem('sessionId', sessionId);
```

### 2. 记忆清理

**建议策略**:
- 用户主动清空：提供"重新开始"按钮
- 定期清理：超过7天未互动的会话
- 隐私保护：用户登出时清空

### 3. 错误处理

```java
try {
    String response = agent.chat(sessionId, userMessage);
} catch (Exception e) {
    // 降级到普通模式
    logger.error("Soul comfort mode failed, fallback to normal mode", e);
    return normalChatService.chat(request);
}
```

### 4. 性能优化

**记忆限制**:
- 短期记忆：50条消息
- 情绪历史：20条记录
- 重要话题：10个

**异步处理**:
```java
// 情绪分析可以异步执行
CompletableFuture.supplyAsync(() ->
    reflectionService.analyzeEmotion(message)
);
```

## 监控指标

### 关键指标

1. **会话质量**
   - 平均对话轮数
   - 用户满意度
   - 情绪变化趋势

2. **系统性能**
   - 响应时间
   - 内存使用
   - LLM调用次数

3. **功能使用**
   - Soul Comfort模式使用率
   - 记忆命中率
   - 反思功能触发频率

### 日志示例

```
[SoulComfortAgent] Session: user-123, Emotion: 焦虑 → 平静
[ConversationMemory] Session: user-123, Messages: 15, Topics: 3
[ReflectionService] Analyzed emotion: 焦虑, Topics: [工作压力, 人际关系]
```

## 扩展指南

### 添加新的情绪类型

1. 在 `ReflectionService` 中扩展情绪词库
2. 更新系统提示以识别新情绪
3. 调整回应策略

### 自定义记忆策略

```java
public class CustomMemoryStrategy extends ConversationMemory {
    @Override
    public void addMessage(String sessionId, ConversationMessage message) {
        // 自定义逻辑
        super.addMessage(sessionId, message);
    }
}
```

### 集成更多反思能力

```java
public class ExtendedReflectionService extends ReflectionService {
    public String analyzePersonality(List<ConversationMessage> history) {
        // 分析用户性格特征
    }

    public String detectBehavioralPattern(UserMemory memory) {
        // 识别行为模式
    }
}
```

## 注意事项

### ⚠️ 重要提醒

1. **不是专业心理咨询**
   - Agent是陪伴工具，不能替代专业心理咨询
   - 遇到严重心理问题应建议用户寻求专业帮助

2. **隐私保护**
   - 妥善管理用户记忆数据
   - 实施数据加密和访问控制
   - 遵守数据保护法规

3. **伦理考虑**
   - 不做评判
   - 不给出可能造成伤害的建议
   - 识别并处理自杀倾向等紧急情况

4. **技术限制**
   - LLM可能产生不准确的回应
   - 记忆系统有容量限制
   - 情绪分析可能不够精确

## 故障排除

### 问题 1: 记忆未生效

**检查**:
- sessionId是否正确传递
- ConversationMemory是否正确初始化
- 日志中是否有记忆相关错误

### 问题 2: 情绪分析不准确

**解决**:
- 检查LLM Provider配置
- 调整温度参数（建议0.3-0.5）
- 优化系统提示

### 问题 3: 响应时间过长

**优化**:
- 减少历史消息数量
- 使用异步处理
- 缓存常见问题的回应

## 未来规划

- [ ] 多语言支持
- [ ] 情绪可视化图表
- [ ] 更精细的人格分析
- [ ] 主动关怀提醒
- [ ] 长期成长追踪
- [ ] 多模态交互（语音、图像）

---

**记住：真正的答案往往就在对方的心里。我们要做的，是帮他们找到那扇门的钥匙。** 🏮
