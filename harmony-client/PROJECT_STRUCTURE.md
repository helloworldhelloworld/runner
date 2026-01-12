# 项目结构说明 📁

## 完整目录树

```
harmony-client/
├── README.md                                    # 项目说明文档
├── SETUP.md                                     # 快速开始指南
├── DESIGN.md                                    # 设计文档
├── PROJECT_STRUCTURE.md                         # 本文件
├── .gitignore                                   # Git 忽略配置
├── build-profile.json5                          # 构建配置
├── oh-package.json5                             # 包管理配置
│
└── entry/                                       # 主模块
    └── src/main/
        ├── module.json5                         # 模块配置
        │
        ├── ets/                                 # ArkTS 源代码
        │   ├── entryability/                    # 应用入口
        │   │   ├── EntryAbility.ets            # Ability 入口类
        │   │   └── AppConfig.json5             # 应用配置
        │   │
        │   ├── pages/                           # 页面
        │   │   └── ChatPage.ets                # 聊天主页面
        │   │
        │   ├── common/                          # 通用组件
        │   │   └── MessageItem.ets             # 消息气泡组件
        │   │
        │   ├── model/                           # 数据模型
        │   │   ├── Message.ets                 # 消息模型
        │   │   └── ChatApi.ets                 # API 模型
        │   │
        │   ├── service/                         # 服务层
        │   │   └── ChatService.ets             # 聊天服务（API调用）
        │   │
        │   └── viewmodel/                       # 视图模型
        │       └── ChatViewModel.ets           # 聊天视图模型
        │
        └── resources/                           # 资源文件
            └── base/
                ├── element/                     # UI 元素
                │   ├── color.json              # 颜色资源
                │   └── string.json             # 文本资源
                ├── media/                       # 媒体资源
                └── profile/                     # 配置文件
                    └── main_pages.json         # 页面路由配置
```

## 核心文件说明

### 📄 配置文件

#### `oh-package.json5`
**用途**: 项目包管理配置
```json5
{
  "name": "jieyouzahuodian",
  "version": "1.0.0",
  "dependencies": { ... }
}
```
**包含内容**:
- 项目名称和版本
- 依赖包列表
- DevEco Studio 工具版本

#### `build-profile.json5`
**用途**: 构建配置
**包含内容**:
- 签名配置
- 产品配置
- 模块配置

#### `module.json5`
**用途**: 模块配置
**包含内容**:
- 模块类型和名称
- 支持的设备类型
- Ability 配置
- 页面路由

### 🎨 资源文件

#### `resources/base/element/color.json`
**用途**: 颜色资源定义
**定义的颜色**:
- `background_color`: 背景色
- `primary_text`: 主文本色
- `accent_color`: 主题色
- `user_message_bg`: 用户消息背景
- `assistant_message_bg`: 助手消息背景
- 等等...

#### `resources/base/element/string.json`
**用途**: 文本资源定义
**定义的字符串**:
- `app_name`: 应用名称
- `welcome_message`: 欢迎消息
- `input_placeholder`: 输入框提示
- 等等...

#### `resources/base/profile/main_pages.json`
**用途**: 页面路由配置
```json
{
  "src": [
    "pages/ChatPage"  // 主页面路径
  ]
}
```

### 💻 代码文件

#### 📱 入口层

##### `EntryAbility.ets`
**职责**: 应用生命周期管理
**主要方法**:
```typescript
onCreate()              // 应用创建
onWindowStageCreate()   // 窗口创建
onForeground()          // 前台运行
onBackground()          // 后台运行
onDestroy()             // 应用销毁
```

#### 📄 页面层

##### `ChatPage.ets`
**职责**: 聊天主界面
**组件结构**:
```
ChatPage
├── buildHeader()       // 顶部标题栏
├── buildMessageList()  // 消息列表
└── buildInputArea()    // 输入区域
```

**状态管理**:
```typescript
@State viewModel: ChatViewModel  // 视图模型
@State inputText: string         // 输入文本
private scroller: Scroller       // 滚动控制器
```

#### 🧩 组件层

##### `MessageItem.ets`
**职责**: 单条消息展示
**支持的消息类型**:
- 用户消息（蓝色气泡，右对齐）
- 助手消息（灰色气泡，左对齐，带图标）
- 系统消息（居中显示）

**Props**:
```typescript
@Prop message: Message  // 消息数据
```

#### 📊 模型层

##### `Message.ets`
**职责**: 消息数据结构
```typescript
export class Message {
  id: string;           // 唯一标识
  content: string;      // 消息内容
  role: MessageRole;    // 角色（用户/助手/系统）
  timestamp: number;    // 时间戳
  isLoading?: boolean;  // 是否加载中
}

export enum MessageRole {
  USER = 'user',
  ASSISTANT = 'assistant',
  SYSTEM = 'system'
}
```

##### `ChatApi.ets`
**职责**: API 数据结构
```typescript
export interface ChatRequest {
  message: string;
  activeSkills: string[];
  useToolCalling: boolean;
}

export interface ChatResponse {
  response: string;
  toolCallsUsed?: string[];
  skillsApplied?: string[];
  metadata?: { ... };
  error?: string;
}

export interface Skill { ... }
export interface Tool { ... }
```

#### 🌐 服务层

##### `ChatService.ets`
**职责**: 后端 API 通信
**提供的方法**:
```typescript
static sendMessage()    // 发送聊天消息
static getSkills()      // 获取技能列表
static getTools()       // 获取工具列表
static healthCheck()    // 健康检查
```

**配置**:
```typescript
private static readonly BASE_URL = 'http://localhost:8080';
```

#### 🎯 视图模型层

##### `ChatViewModel.ets`
**职责**: 页面状态管理和业务逻辑
**状态**:
```typescript
messages: Message[]      // 消息列表
inputText: string        // 输入文本
isConnected: boolean     // 连接状态
isSending: boolean       // 发送状态
```

**方法**:
```typescript
initialize()            // 初始化
sendMessage()           // 发送消息
clearMessages()         // 清空消息
```

## 代码流程

### 应用启动流程

```
1. EntryAbility.onCreate()
   ↓
2. EntryAbility.onWindowStageCreate()
   ↓
3. 加载 ChatPage
   ↓
4. ChatPage.aboutToAppear()
   ↓
5. ChatViewModel.initialize()
   ↓
6. ChatService.healthCheck()
   ↓
7. 显示欢迎消息
```

### 消息发送流程

```
用户在 ChatPage 输入
   ↓
点击发送按钮
   ↓
ChatPage.sendMessage()
   ↓
ChatViewModel.sendMessage()
   ├─ 添加用户消息到 messages
   ├─ 清空输入框
   ├─ 显示加载提示
   ↓
ChatService.sendMessage()
   ├─ 创建 HTTP 请求
   ├─ 发送到后端 /api/chat
   ├─ 接收响应
   ↓
ChatViewModel 接收响应
   ├─ 移除加载提示
   ├─ 添加助手消息
   ↓
ChatPage 自动滚动到底部
```

### 数据流向

```
UI 层 (ChatPage)
   ↕
ViewModel 层 (ChatViewModel)
   ↕
Service 层 (ChatService)
   ↕
后端 API (Spring Boot)
```

## 依赖关系

```
ChatPage
  └─ depends on ChatViewModel
      └─ depends on ChatService
          └─ uses ChatApi interfaces
              └─ uses Message model

MessageItem
  └─ depends on Message model
```

## 命名规范

### 文件命名

- **页面**: `XxxPage.ets` (PascalCase)
- **组件**: `XxxItem.ets` / `XxxComponent.ets`
- **模型**: `Xxx.ets` (简洁名词)
- **服务**: `XxxService.ets`
- **视图模型**: `XxxViewModel.ets`

### 变量命名

```typescript
// 类名: PascalCase
class ChatViewModel { }

// 变量/方法: camelCase
let inputText: string;
function sendMessage() { }

// 常量: UPPER_SNAKE_CASE
const BASE_URL = 'http://...';

// 私有成员: _前缀
private _internalState: any;

// 组件装饰器
@Entry @Component @State @Prop
```

## 资源引用

### 在代码中引用资源

```typescript
// 引用字符串资源
$r('app.string.welcome_message')

// 引用颜色资源
$r('app.color.primary_text')

// 引用系统图标
$r('sys.symbol.paperplane_fill')

// 引用媒体资源
$r('app.media.app_icon')
```

## 扩展指南

### 添加新页面

1. 在 `pages/` 目录创建 `NewPage.ets`
2. 在 `main_pages.json` 添加路由
3. 实现页面组件
4. 使用 `router.pushUrl()` 导航

### 添加新组件

1. 在 `common/` 目录创建 `NewComponent.ets`
2. 定义 `@Component` 结构体
3. 在页面中导入并使用

### 添加新服务

1. 在 `service/` 目录创建 `NewService.ets`
2. 定义静态方法
3. 在 ViewModel 中调用

### 添加新模型

1. 在 `model/` 目录创建 `NewModel.ets`
2. 定义 interface 或 class
3. 在需要的地方导入使用

## 性能优化建议

### 1. 状态管理

```typescript
// ❌ 不好 - 频繁触发更新
this.messages.push(newMessage);

// ✅ 好 - 批量更新
this.messages = [...this.messages, newMessage];
```

### 2. 列表渲染

```typescript
// ✅ 提供唯一 key
ForEach(this.messages,
  (item: Message) => { MessageItem({ message: item }) },
  (item: Message) => item.id  // 唯一标识
)
```

### 3. 图片加载

```typescript
// ✅ 使用合适的图片格式和大小
Image($r('app.media.icon'))
  .width(24)
  .height(24)
  .objectFit(ImageFit.Contain)
```

## 调试技巧

### 日志输出

```typescript
// 使用 console
console.info('Info message');
console.error('Error message');
console.debug('Debug message');

// 查看 Hilog
// DevEco Studio → Hilog 面板
```

### 断点调试

```typescript
// 在关键位置设置断点
async sendMessage(content: string) {
  // 设置断点 ← 这里
  const response = await ChatService.sendMessage(content);
  // 设置断点 ← 这里
}
```

## 测试建议

### 单元测试

```typescript
// 测试 ViewModel 逻辑
describe('ChatViewModel', () => {
  it('should send message', async () => {
    const vm = new ChatViewModel();
    await vm.sendMessage('Hello');
    expect(vm.messages.length).toBe(2); // 用户+助手
  });
});
```

### 集成测试

```typescript
// 测试 Service API 调用
describe('ChatService', () => {
  it('should connect to backend', async () => {
    const isConnected = await ChatService.healthCheck();
    expect(isConnected).toBe(true);
  });
});
```

## 常见问题

### Q: 如何修改后端地址？

A: 编辑 `service/ChatService.ets`:
```typescript
private static readonly BASE_URL = 'http://your-ip:8080';
```

### Q: 如何添加新的消息类型？

A:
1. 在 `Message.ets` 添加新的 `MessageRole`
2. 在 `MessageItem.ets` 添加新的渲染逻辑

### Q: 如何持久化聊天记录？

A:
1. 使用 `@ohos.data.preferences` 存储
2. 在 `ChatViewModel` 中实现保存和加载逻辑

---

**结构清晰，代码才能优雅。** 📐
