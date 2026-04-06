# Claude Skills 支持

基于 Anthropic 官方的 [Skills 规范](https://github.com/anthropics/skills) 实现。

## Claude Skills vs Tool Calling 对比

### Tool Calling (函数调用)
```java
// 严格的函数签名
{
  "name": "add",
  "input_schema": {
    "type": "object",
    "properties": {
      "a": {"type": "number"},
      "b": {"type": "number"}
    }
  }
}

// Java 端执行
public Integer add(int a, int b) {
    return a + b;
}
```

**特点**:
- ✅ 精确的类型约束
- ✅ 确定性执行
- ✅ 适合 API 调用、数据库查询
- ❌ 不灵活，需要提前定义所有参数
- ❌ 无法处理复杂的工作流程

### Claude Skills (指令包)
```yaml
---
name: document-creator
description: Creates professional documents following brand guidelines
---

# Document Creator Skill

## Instructions
When creating documents:
1. Always use company brand colors (#1E3A8A)
2. Include copyright footer
3. Use Arial font family

## Examples
- Product brief with specs
- Meeting agenda with action items

## Resources
- brand-guidelines.pdf
- company-template.docx
```

**特点**:
- ✅ 自然语言指令，灵活
- ✅ 可包含示例、指南、资源文件
- ✅ 适合复杂工作流程和创意任务
- ✅ Claude 根据上下文自适应执行
- ❌ 非确定性（Claude 解释指令）
- ❌ 需要 Claude 理解和遵循

## 何时使用哪个？

### 使用 Tool Calling 当：
- 需要调用精确的 API
- 数据库操作（CRUD）
- 数学计算
- 系统命令执行
- 需要确定性结果

### 使用 Claude Skills 当：
- 创建文档/演示文稿
- 遵循品牌指南
- 复杂的创意任务
- 需要上下文理解
- 工作流程指导

## 架构

```
┌─────────────────────────────────────┐
│         Skill Package               │
├─────────────────────────────────────┤
│  SKILL.md (YAML + Markdown)         │
│  ├─ name                            │
│  ├─ description                     │
│  └─ instructions (自然语言)         │
│                                     │
│  Resources (可选)                   │
│  ├─ templates/                      │
│  ├─ examples/                       │
│  └─ data/                           │
└─────────────────────────────────────┘
           ↓
    SkillLoader
           ↓
    SkillRegistry
           ↓
  Inject into Conversation
           ↓
     Claude reads & follows
```

## 使用示例

### 1. 创建 Skill

创建 `my-skill/SKILL.md`:

```yaml
---
name: brand-document
description: Creates documents following company brand guidelines
version: 1.0.0
author: Your Team
---

# Brand Document Creator

Creates professional documents that follow our company branding.

## Brand Guidelines

- **Primary Color**: #1E3A8A (Navy Blue)
- **Secondary Color**: #10B981 (Green)
- **Font**: Arial, sans-serif
- **Tone**: Professional, friendly, clear

## Instructions

When creating documents:

1. **Header**: Use navy blue background with white text
2. **Body**: Use Arial font, 11pt for body text
3. **Headings**: Navy blue, bold, 14pt
4. **Footer**: Include copyright and date

## Examples

### Product Brief
```
[Navy Header]
PRODUCT BRIEF: New Feature

[Body]
Overview: This document describes...
Features:
- Feature 1
- Feature 2

[Footer]
© 2024 Company Name | Confidential
```

### Meeting Agenda
```
[Navy Header]
MEETING AGENDA - Q1 Planning

Date: January 15, 2024
Attendees: Team leads

[Body]
1. Q1 Goals Review
2. Budget Discussion
3. Action Items

[Footer]
© 2024 Company Name
```

## Guidelines

- Always start with a clear title
- Use bullet points for lists
- Keep paragraphs concise
- Include date and authorship
- Follow color scheme strictly
```

### 2. 添加资源文件

```
my-skill/
  ├── SKILL.md
  ├── templates/
  │   ├── brief-template.md
  │   └── agenda-template.md
  └── examples/
      └── sample-output.pdf
```

### 3. Java 代码使用

```java
import com.lightweightai.kernel.skill.*;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.claude.ClaudeProvider;

// 1. 创建 Skill Registry
SkillRegistry registry = new SkillRegistry();

// 2. 加载 Skills 从目录
Path skillsDir = Paths.get("/path/to/skills");
registry.loadSkills(skillsDir);

// 3. 激活需要的 Skills
registry.activateSkill("brand-document");

// 4. 创建对话消息
List<ConversationMessage> messages = List.of(
    ConversationMessage.builder()
        .role(MessageRole.USER)
        .textContent("Create a product brief for our new AI feature")
        .build()
);

// 5. 注入 Skills 到对话中
List<ConversationMessage> enrichedMessages = registry.injectSkills(messages);

// 6. 调用 Claude
ClaudeProvider provider = new ClaudeProvider(apiKey, "claude-3-5-sonnet-20241022");
LLMResponse response = provider.complete(
    enrichedMessages,
    LLMOptions.builder().maxTokens(4096).build()
);

// 7. Claude 会遵循 Skill 中的指令创建文档
System.out.println(response.getMessage().getTextContent());
```

### 4. 动态激活 Skills

```java
// 基于用户查询自动建议 Skills
String userQuery = "Create a meeting agenda";
List<String> suggested = registry.suggestSkills(userQuery);

System.out.println("Suggested skills: " + suggested);
// Output: [brand-document, meeting-planner]

// 激活建议的 Skills
suggested.forEach(registry::activateSkill);
```

## API 参考

### Skill

```java
Skill skill = Skill.builder()
    .name("my-skill")
    .description("Description of what this skill does")
    .instructions("Detailed instructions in Markdown")
    .addMetadata("version", "1.0.0")
    .addResource("template.md", templateContent)
    .build();

// 转换为 Prompt 上下文
String context = skill.toPromptContext();

// 转换为系统消息
String systemMsg = skill.toSystemMessage();

// 访问资源
byte[] resource = skill.getResource("template.md");
String textResource = skill.getResourceAsString("template.md");
```

### SkillLoader

```java
// 加载单个 Skill
Skill skill = SkillLoader.loadSkill(Paths.get("/path/to/skill"));

// 加载目录下所有 Skills
Map<String, Skill> skills = SkillLoader.loadSkills(Paths.get("/skills/directory"));

// 创建示例 Skill
SkillLoader.createSampleSkill(Paths.get("/output"), "my-sample-skill");
```

### SkillRegistry

```java
SkillRegistry registry = new SkillRegistry();

// 注册
registry.registerSkill(skill);
registry.registerSkills(skillList);
registry.loadSkills(skillsDirectory);

// 激活/停用
registry.activateSkill("skill-name");
registry.activateSkills("skill1", "skill2");
registry.deactivateSkill("skill-name");
registry.deactivateAllSkills();

// 查询
boolean has = registry.hasSkill("skill-name");
boolean active = registry.isActive("skill-name");
Skill skill = registry.getSkill("skill-name");
Collection<Skill> all = registry.getAllSkills();
List<Skill> activeSkills = registry.getActiveSkills();

// 构建系统消息
String systemMsg = registry.buildSkillsSystemMessage();
ConversationMessage msg = registry.createSkillsSystemMessage();

// 注入到对话
List<ConversationMessage> enriched = registry.injectSkills(messages);

// 建议相关 Skills
List<String> suggested = registry.suggestSkills("user query");
```

## 完整示例：文档生成系统

```java
public class DocumentGeneratorApp {
    public static void main(String[] args) throws IOException {
        // 1. 设置 Skills
        SkillRegistry registry = new SkillRegistry();
        registry.loadSkills(Paths.get("./skills"));

        // 2. 激活文档相关 Skills
        registry.activateSkills(
            "brand-document",
            "technical-writer",
            "markdown-formatter"
        );

        // 3. 创建 LLM Provider
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        ClaudeProvider provider = new ClaudeProvider(
            apiKey,
            "claude-3-5-sonnet-20241022"
        );

        // 4. 用户请求
        Scanner scanner = new Scanner(System.in);
        System.out.print("What document do you want to create? ");
        String userRequest = scanner.nextLine();

        // 5. 准备对话
        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent(userRequest)
                .build()
        );

        // 6. 注入 Skills
        messages = registry.injectSkills(messages);

        // 7. 调用 Claude
        System.out.println("\nGenerating document with active skills:");
        registry.getActiveSkills().forEach(skill ->
            System.out.println("  - " + skill.getName())
        );

        LLMResponse response = provider.complete(
            messages,
            LLMOptions.builder().maxTokens(8192).build()
        );

        // 8. 输出结果
        System.out.println("\n=== Generated Document ===\n");
        System.out.println(response.getMessage().getTextContent());
    }
}
```

## Skill 最佳实践

### 1. 清晰的描述
```yaml
# ✅ 好
description: Creates technical documentation with code examples, following our style guide

# ❌ 差
description: Makes docs
```

### 2. 具体的指令
```markdown
## Instructions

# ✅ 好
1. Start with an executive summary (3-5 sentences)
2. Use heading hierarchy: H1 for title, H2 for sections
3. Include code blocks with language tags: ```python
4. Add table of contents for documents >1000 words

# ❌ 差
- Write good documentation
- Format nicely
```

### 3. 实用的示例
```markdown
## Examples

# ✅ 好 - 完整示例
### API Endpoint Documentation
```
GET /api/users/{id}

Returns user details by ID.

**Parameters:**
- id (integer, required): User ID

**Response:**
{
  "id": 123,
  "name": "John Doe"
}
```

# ❌ 差 - 模糊示例
- Document APIs
- Show examples
```

### 4. 组织资源
```
skill-name/
  ├── SKILL.md                    # 主文件
  ├── templates/                  # 模板
  │   ├── basic-template.md
  │   └── advanced-template.md
  ├── examples/                   # 示例
  │   ├── example-1.md
  │   └── example-2.md
  └── assets/                     # 资产
      ├── logo.png
      └── style-guide.pdf
```

## Tool Calling + Claude Skills 组合使用

两者可以完美配合：

```java
// Tool Calling: 获取数据
toolExecutor.registerFunction("get_user_data", userData -> {
    return fetchFromDatabase(userData);
});

// Claude Skill: 使用数据创建报告
registry.activateSkill("report-generator");

// Claude 会：
// 1. 使用 get_user_data tool 获取数据
// 2. 遵循 report-generator skill 创建专业报告
```

## 与 Anthropic 官方 Skills 的兼容性

我们的实现完全兼容 Anthropic 的规范：

```yaml
---
name: skill-name             # ✅ 兼容
description: What it does    # ✅ 兼容
version: 1.0.0              # ✅ 支持（可选元数据）
author: Your Name           # ✅ 支持（可选元数据）
tags: [doc, api]            # ✅ 支持（可选元数据）
---

# Skill Content               # ✅ 完全兼容 Markdown
```

你可以直接使用 [anthropics/skills](https://github.com/anthropics/skills) 仓库中的任何 Skill！

## 性能考虑

- **Context 大小**: Skills 会增加 prompt 长度
  - 每个 skill 约 500-2000 tokens
  - 只激活需要的 skills

- **推荐**:
  - 同时激活 ≤ 5 个 skills
  - 使用 `suggestSkills()` 动态激活相关 skills
  - 大型 skills 考虑分拆

## 调试

```java
// 查看将要发送给 Claude 的完整 context
String fullContext = registry.buildSkillsSystemMessage();
System.out.println("=== Skills Context ===");
System.out.println(fullContext);

// 查看 skill 详情
Skill skill = registry.getSkill("my-skill");
System.out.println("Instructions length: " + skill.getInstructions().length());
System.out.println("Resources: " + skill.getResources().keySet());
```

## 相关文档

- [Anthropic Skills 官方仓库](https://github.com/anthropics/skills)
- [agentskills.io 规范](http://agentskills.io)
- [创建自定义 Skills 指南](https://support.claude.com/en/articles/12512198-creating-custom-skills)
- [Tool Calling 文档](./CLAUDE_SKILLS_DEMO.md)
- [异步非阻塞模型](./ASYNC_NONBLOCKING_MODEL.md)
