# Instruction Package Abstraction Layer

## 概述

为了支持多种 LLM 提供商，我们引入了一个提供商无关的抽象层，同时保持对现有 Claude Skills 代码的向后兼容。

## 架构层次

```
┌─────────────────────────────────────────────────────────┐
│              Application Code                           │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│         InstructionRegistry (抽象层)                     │
│  - 提供商无关的指令管理                                  │
│  - 支持任何 LLM 提供商                                   │
└─────────────────────────────────────────────────────────┘
                         ↓
            ┌────────────┴────────────┐
            ↓                         ↓
┌──────────────────────┐  ┌──────────────────────┐
│  ProviderAdapter     │  │ InstructionPackage   │
│  (接口)              │  │ (接口)               │
└──────────────────────┘  └──────────────────────┘
            ↓                         ↓
┌──────────────────────┐  ┌──────────────────────┐
│ ClaudeProviderAdapter│  │ ClaudeSkillAdapter   │
│ (Claude 实现)        │  │ (Skill → Package)    │
└──────────────────────┘  └──────────────────────┘
            ↓                         ↓
┌──────────────────────┐  ┌──────────────────────┐
│  SkillRegistry       │  │      Skill           │
│  (遗留，向后兼容)    │  │  (Claude 特定)       │
└──────────────────────┘  └──────────────────────┘
```

## 核心接口

### 1. InstructionPackage

提供商无关的指令包接口。

```java
public interface InstructionPackage {
    String getName();
    String getDescription();
    String getInstructions();
    Map<String, String> getMetadata();
    Map<String, byte[]> getResources();
    String toPromptContext();
    String getFormat();  // "anthropic-skill", "openai-prompt", etc.
}
```

**设计理念**:
- 格式无关：支持任何指令格式
- 提供商无关：不绑定特定 LLM
- 可扩展：支持元数据和资源

### 2. ProviderAdapter

提供商适配器接口，负责将指令转换为特定提供商的格式。

```java
public interface ProviderAdapter {
    String getProviderName();
    ConversationMessage formatAsSystemMessage(InstructionPackage pkg);
    ConversationMessage formatAsUserPrefix(InstructionPackage pkg, String msg);
    List<ConversationMessage> injectInstructions(
        List<ConversationMessage> messages,
        List<InstructionPackage> packages
    );
    ProviderCapabilities getCapabilities();
}
```

**设计理念**:
- 适配不同 LLM 的消息格式
- 处理提供商特定的限制
- 提供能力描述

### 3. InstructionRegistry

通用指令注册表。

```java
public class InstructionRegistry {
    public InstructionRegistry(ProviderAdapter adapter);

    void registerPackage(InstructionPackage pkg);
    void activatePackage(String name);
    List<ConversationMessage> injectInstructions(List<ConversationMessage> messages);
    List<String> suggestPackages(String query);
}
```

**设计理念**:
- 使用依赖注入选择提供商
- API 与提供商无关
- 支持动态切换适配器

## 使用方式

### 方式 1: 使用新抽象层（推荐用于多提供商）

```java
// 1. 创建适配器
ProviderAdapter adapter = ProviderAdapterFactory.claude();

// 2. 创建注册表
InstructionRegistry registry = new InstructionRegistry(adapter);

// 3. 注册指令包
InstructionPackage pkg = ...; // 任何实现
registry.registerPackage(pkg);

// 4. 激活并使用
registry.activatePackage("my-package");
List<ConversationMessage> enriched = registry.injectInstructions(messages);
```

### 方式 2: 使用 Claude Skills（向后兼容）

```java
// 旧代码继续工作
SkillRegistry registry = new SkillRegistry();
registry.registerSkill(skill);
registry.activateSkill("my-skill");
List<ConversationMessage> enriched = registry.injectSkills(messages);
```

### 方式 3: 混合使用

```java
// 将现有 Skill 适配到新抽象层
Skill skill = SkillLoader.loadSkill(path);
InstructionPackage pkg = new ClaudeSkillAdapter(skill);

InstructionRegistry registry = new InstructionRegistry(
    ProviderAdapterFactory.claude()
);
registry.registerPackage(pkg);
```

## 提供商适配器

### Claude Adapter (已实现)

```java
ClaudeProviderAdapter adapter = ClaudeProviderAdapter.getInstance();

// 能力
ProviderCapabilities caps = adapter.getCapabilities();
caps.supportsSystemMessages();      // true
caps.supportsInstructions();        // true
caps.supportsResources();           // true
caps.getMaxInstructionLength();     // 200000
caps.supportsMultipleInstructions(); // true
```

### OpenAI Adapter (未来实现)

```java
// 示例：未来的 OpenAI 支持
public class OpenAIProviderAdapter implements ProviderAdapter {
    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public ConversationMessage formatAsSystemMessage(InstructionPackage pkg) {
        // OpenAI 格式：简洁的系统消息
        return ConversationMessage.builder()
            .role(MessageRole.SYSTEM)
            .textContent(formatForOpenAI(pkg))
            .build();
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return new ProviderCapabilities(
            true,   // supportsSystemMessages
            true,   // supportsInstructions
            false,  // supportsResources (limited)
            128000, // maxInstructionLength (GPT-4)
            true    // supportsMultipleInstructions
        );
    }
}
```

### Gemini Adapter (未来实现)

```java
// 示例：未来的 Gemini 支持
public class GeminiProviderAdapter implements ProviderAdapter {
    @Override
    public List<ConversationMessage> injectInstructions(
        List<ConversationMessage> messages,
        List<InstructionPackage> packages
    ) {
        // Gemini 可能需要不同的注入方式
        // 例如：作为上下文而不是系统消息
        return injectAsContext(messages, packages);
    }
}
```

## 迁移指南

### 从 SkillRegistry 迁移到 InstructionRegistry

**Before:**
```java
SkillRegistry registry = new SkillRegistry();
registry.loadSkills(Paths.get("./skills"));
registry.activateSkill("brand-document");
List<ConversationMessage> enriched = registry.injectSkills(messages);
```

**After:**
```java
InstructionRegistry registry = new InstructionRegistry(
    ProviderAdapterFactory.claude()
);

// 方式 A: 继续使用 Skill
Skill skill = SkillLoader.loadSkill(Paths.get("./skills/brand-document"));
registry.registerPackage(new ClaudeSkillAdapter(skill));

// 方式 B: 批量加载并转换
Map<String, Skill> skills = SkillLoader.loadSkills(Paths.get("./skills"));
skills.values().forEach(skill ->
    registry.registerPackage(new ClaudeSkillAdapter(skill))
);

registry.activatePackage("brand-document");
List<ConversationMessage> enriched = registry.injectInstructions(messages);
```

### 好处

1. **提供商无关**: 代码不绑定到 Claude
2. **易于扩展**: 添加新提供商只需实现适配器
3. **向后兼容**: 现有 Skill 代码无需修改
4. **能力感知**: 自动适配不同提供商的限制

## 扩展示例

### 添加自定义适配器

```java
public class CustomProviderAdapter implements ProviderAdapter {
    @Override
    public String getProviderName() {
        return "my-llm";
    }

    @Override
    public boolean supports(String providerName) {
        return "my-llm".equalsIgnoreCase(providerName);
    }

    // 实现其他方法...
}

// 注册
ProviderAdapterFactory.registerInstance("my-llm", new CustomProviderAdapter());

// 使用
InstructionRegistry registry = new InstructionRegistry(
    ProviderAdapterFactory.create("my-llm")
);
```

### 创建自定义指令包

```java
public class CustomInstructionPackage implements InstructionPackage {
    @Override
    public String getName() {
        return "custom-package";
    }

    @Override
    public String getFormat() {
        return "custom-format";
    }

    @Override
    public String toPromptContext() {
        // 自定义格式化逻辑
        return buildCustomContext();
    }

    // 实现其他方法...
}
```

## 设计决策

### 为什么使用适配器模式？

1. **分离关注点**: 指令内容与提供商格式分离
2. **开闭原则**: 对扩展开放，对修改关闭
3. **单一职责**: 每个适配器只负责一个提供商

### 为什么保留 SkillRegistry？

1. **向后兼容**: 不破坏现有代码
2. **渐进迁移**: 用户可以逐步迁移
3. **专用优化**: Claude 特定优化保留

### 为什么使用接口而非抽象类？

1. **灵活性**: 实现可以继承其他类
2. **多重实现**: 可以组合多个接口
3. **依赖反转**: 依赖抽象而非具体

## 性能考虑

### 适配器开销

- 适配器调用是轻量级的
- 单例模式避免重复创建
- 无反射（除了工厂）

### 内存使用

- 指令包是不可变的
- 共享注册表减少重复
- 资源按需加载

## 测试策略

### 单元测试

```java
@Test
void shouldWorkWithDifferentAdapters() {
    InstructionPackage pkg = createTestPackage();

    // Test with Claude
    InstructionRegistry claudeRegistry = new InstructionRegistry(
        ProviderAdapterFactory.claude()
    );
    claudeRegistry.registerPackage(pkg);

    // Test with custom adapter
    InstructionRegistry customRegistry = new InstructionRegistry(
        new CustomAdapter()
    );
    customRegistry.registerPackage(pkg);

    // Both should work
    assertNotNull(claudeRegistry.injectInstructions(messages));
    assertNotNull(customRegistry.injectInstructions(messages));
}
```

## 最佳实践

### 1. 优先使用工厂创建适配器

```java
// ✅ 好
ProviderAdapter adapter = ProviderAdapterFactory.claude();

// ❌ 差
ProviderAdapter adapter = new ClaudeProviderAdapter();
```

### 2. 缓存注册表实例

```java
// ✅ 好
private static final InstructionRegistry REGISTRY =
    new InstructionRegistry(ProviderAdapterFactory.claude());

// ❌ 差
public void process() {
    InstructionRegistry registry = new InstructionRegistry(...);
    // 每次调用都创建新实例
}
```

### 3. 使用能力检查

```java
// ✅ 好
ProviderCapabilities caps = registry.getCapabilities();
if (caps.supportsResources()) {
    pkg.addResource("template.md", content);
}

// ❌ 差
pkg.addResource("template.md", content); // 可能不支持
```

## 未来扩展

### 计划中的功能

1. **更多提供商适配器**
   - OpenAI/GPT-4
   - Google Gemini
   - Meta Llama
   - Cohere

2. **高级指令包格式**
   - LangChain Prompt Templates
   - OpenAI Custom Instructions
   - Semantic Kernel Skills

3. **智能路由**
   - 基于能力自动选择提供商
   - 降级策略
   - 负载均衡

4. **验证层**
   - 指令包验证
   - 输出验证
   - 合规性检查

## 总结

新的抽象层提供了：

✅ **提供商无关** - 支持任何 LLM
✅ **向后兼容** - 现有代码继续工作
✅ **易于扩展** - 添加新提供商很简单
✅ **类型安全** - 编译时检查
✅ **经过测试** - 18+ 单元测试
✅ **生产就绪** - Claude 实现已验证

同时保留了：

✅ **Claude Skills** - 完整功能保留
✅ **简单易用** - API 保持简洁
✅ **高性能** - 零额外开销

这是一个**渐进式升级**，用户可以：
- 继续使用 SkillRegistry（Claude 专用）
- 逐步迁移到 InstructionRegistry（多提供商）
- 两者混合使用（过渡期）
