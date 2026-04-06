# WebSocket LLM Provider - 完整指南

## 概述

WebSocketLLMProvider 是基于 **OkHttp WebSocket** 的 LLM Provider 实现，提供双向实时通信能力，支持 Claude Skills (Function Calling)。

### 核心特性

✅ **实时双向通信** - WebSocket 全双工通信
✅ **流式响应** - 逐 token 实时输出
✅ **Function Calling** - 完整支持 Claude Skills 范式
✅ **自动重连** - 断线自动重连机制
✅ **心跳保活** - 30秒心跳检测
✅ **异步非阻塞** - CompletableFuture 异步模型
✅ **轻量依赖** - 基于 OkHttp，无额外依赖

## 架构设计

### 通信协议

```
Client                          Server
  |                               |
  |-------- CHAT_REQUEST -------->|  发起对话
  |                               |
  |<------- TEXT_DELTA -----------|  流式文本片段
  |<------- TEXT_DELTA -----------|
  |                               |
  |<------- TOOL_CALL ------------|  请求工具调用
  |                               |
  |-------- TOOL_RESULT --------->|  工具执行结果
  |                               |
  |<------- TEXT_DELTA -----------|  继续输出
  |<------- CHAT_RESPONSE --------|  完整响应
  |                               |
  |-------- PING ---------------->|  心跳检测
  |<------- PONG -----------------|  心跳响应
```

### 消息类型

#### Client → Server

| 类型 | 说明 | 数据结构 |
|------|------|---------|
| `CHAT_REQUEST` | 发起对话请求 | ChatRequestData |
| `TOOL_RESULT` | 工具执行结果 | ToolResultData |
| `PING` | 心跳检测 | null |

#### Server → Client

| 类型 | 说明 | 数据结构 |
|------|------|---------|
| `CHAT_RESPONSE` | 完整响应 | ChatResponseData |
| `TEXT_DELTA` | 流式文本片段 | TextDeltaData |
| `TOOL_CALL` | 工具调用请求 | ToolCallData |
| `ERROR` | 错误消息 | error 字段 |
| `PONG` | 心跳响应 | null |

## 使用示例

### 示例 1: 基本使用

```java
// 创建 WebSocket Provider
WebSocketLLMProvider provider = new WebSocketLLMProvider(
    "ws://localhost:8080/llm/ws",  // WebSocket URL
    "claude-3-5-sonnet-20241022"    // 模型名称
);

// 连接到服务器
provider.connect().get();

// 发起对话
List<ConversationMessage> messages = List.of(
    ConversationMessage.builder()
        .role(MessageRole.USER)
        .textContent("你好，请介绍一下自己")
        .build()
);

LLMResponse response = provider.complete(messages, LLMOptions.builder().build());
System.out.println("Response: " + response.getMessage().getTextContent());

// 关闭连接
provider.shutdown();
```

### 示例 2: 异步非阻塞调用

```java
WebSocketLLMProvider provider = new WebSocketLLMProvider(
    "ws://localhost:8080/llm/ws",
    "claude-3-5-sonnet-20241022"
);

// 异步连接
provider.connect().thenCompose(v -> {
    // 连接成功后，异步发起对话
    List<ConversationMessage> messages = List.of(
        ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent("计算 123 + 456")
            .build()
    );

    return provider.completeAsync(messages, LLMOptions.builder().build());
}).thenAccept(response -> {
    System.out.println("Got response: " + response.getMessage().getTextContent());
}).exceptionally(error -> {
    System.err.println("Error: " + error.getMessage());
    return null;
});

// 主线程继续执行其他任务...
```

### 示例 3: 流式响应

```java
WebSocketLLMProvider provider = new WebSocketLLMProvider(
    "ws://localhost:8080/llm/ws",
    "claude-3-5-sonnet-20241022"
);

provider.connect().get();

List<ConversationMessage> messages = List.of(
    ConversationMessage.builder()
        .role(MessageRole.USER)
        .textContent("写一首关于春天的诗")
        .build()
);

// 流式处理器
LLMProvider.StreamEventHandler handler = new LLMProvider.StreamEventHandler() {
    @Override
    public void onStart() {
        System.out.println("开始生成...");
    }

    @Override
    public void onTextDelta(String delta) {
        // 实时打印每个文本片段
        System.out.print(delta);
        System.out.flush();
    }

    @Override
    public void onComplete(LLMResponse response) {
        System.out.println("\n\n生成完成！");
    }

    @Override
    public void onError(Throwable error) {
        System.err.println("错误: " + error.getMessage());
    }
};

// 启动流式调用
CompletableFuture<LLMResponse> future = provider.completeStream(
    messages,
    LLMOptions.builder().build(),
    handler
);

// 等待完成
future.get();
```

### 示例 4: Function Calling (Claude Skills)

```java
WebSocketLLMProvider provider = new WebSocketLLMProvider(
    "ws://localhost:8080/llm/ws",
    "claude-3-5-sonnet-20241022"
);

provider.connect().get();

// 定义工具
List<Map<String, Object>> tools = List.of(
    Map.of(
        "name", "calculator",
        "description", "执行数学运算",
        "input_schema", Map.of(
            "type", "object",
            "properties", Map.of(
                "operation", Map.of("type", "string", "enum", List.of("+", "-", "*", "/")),
                "a", Map.of("type", "number"),
                "b", Map.of("type", "number")
            ),
            "required", List.of("operation", "a", "b")
        )
    )
);

List<ConversationMessage> messages = List.of(
    ConversationMessage.builder()
        .role(MessageRole.USER)
        .textContent("请计算 123 * 456")
        .build()
);

LLMOptions options = LLMOptions.builder()
    .toolDefinitions(tools)
    .build();

LLMResponse response = provider.complete(messages, options);

if (response.hasToolCalls()) {
    for (ToolCall toolCall : response.getToolCalls()) {
        System.out.println("工具调用: " + toolCall.getName());
        System.out.println("参数: " + toolCall.getArguments());

        // 执行工具...
        // 然后发送结果回去...
    }
}
```

### 示例 5: 与 ToolCallingLoop 集成

```java
// 创建 WebSocket Provider
WebSocketLLMProvider provider = new WebSocketLLMProvider(
    "ws://localhost:8080/llm/ws",
    "claude-3-5-sonnet-20241022"
);

provider.connect().get();

// 创建 ToolExecutor 并注册函数
ToolExecutor toolExecutor = new ToolExecutor();
toolExecutor.registerFunction("calculator", createCalculatorFunction());
toolExecutor.registerFunction("weather", createWeatherFunction());

// 创建 ToolCallingLoop
ToolCallingLoop loop = ToolCallingLoop.builder()
    .provider(provider)
    .toolExecutor(toolExecutor)
    .maxIterations(10)
    .build();

// 发起对话 - 自动处理工具调用
List<ConversationMessage> messages = List.of(
    ConversationMessage.builder()
        .role(MessageRole.USER)
        .textContent("请计算 100 + 200，然后查询北京天气")
        .build()
);

LLMOptions options = LLMOptions.builder()
    .toolDefinitions(getAllToolDefinitions())
    .build();

// 异步执行，自动处理多轮工具调用
CompletableFuture<LLMResponse> future = loop.executeWithToolsAsync(messages, options);

future.thenAccept(finalResponse -> {
    System.out.println("最终回复: " + finalResponse.getMessage().getTextContent());
});
```

## 后端服务器实现示例

### Node.js + WebSocket Server

```javascript
const WebSocket = require('ws');

const wss = new WebSocket.Server({ port: 8080, path: '/llm/ws' });

wss.on('connection', (ws) => {
    console.log('Client connected');

    ws.on('message', async (message) => {
        try {
            const data = JSON.parse(message);

            if (data.type === 'CHAT_REQUEST') {
                await handleChatRequest(ws, data);
            } else if (data.type === 'TOOL_RESULT') {
                await handleToolResult(ws, data);
            } else if (data.type === 'PING') {
                // 心跳响应
                ws.send(JSON.stringify({
                    type: 'PONG',
                    request_id: data.request_id
                }));
            }
        } catch (error) {
            console.error('Error:', error);
            ws.send(JSON.stringify({
                type: 'ERROR',
                request_id: data.request_id,
                error: error.message
            }));
        }
    });
});

async function handleChatRequest(ws, request) {
    const { request_id, data } = request;
    const { messages, tools, stream } = data;

    if (stream) {
        // 流式响应
        const response = await callClaudeAPI(messages, tools, true);

        // 模拟流式输出
        for await (const chunk of response) {
            if (chunk.type === 'text_delta') {
                ws.send(JSON.stringify({
                    type: 'TEXT_DELTA',
                    request_id,
                    data: {
                        text: chunk.text
                    }
                }));
            } else if (chunk.type === 'tool_use') {
                ws.send(JSON.stringify({
                    type: 'TOOL_CALL',
                    request_id,
                    data: {
                        id: chunk.id,
                        name: chunk.name,
                        input: chunk.input
                    }
                }));
            }
        }

        // 发送完整响应标记
        ws.send(JSON.stringify({
            type: 'CHAT_RESPONSE',
            request_id,
            data: {
                content: fullText,
                tool_calls: toolCalls,
                stop_reason: 'end_turn'
            }
        }));

    } else {
        // 非流式响应
        const response = await callClaudeAPI(messages, tools, false);

        ws.send(JSON.stringify({
            type: 'CHAT_RESPONSE',
            request_id,
            data: {
                content: response.content[0].text,
                tool_calls: response.content.filter(c => c.type === 'tool_use'),
                stop_reason: response.stop_reason,
                usage: {
                    input_tokens: response.usage.input_tokens,
                    output_tokens: response.usage.output_tokens
                }
            }
        }));
    }
}

async function callClaudeAPI(messages, tools, stream) {
    // 调用 Claude API
    const response = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'x-api-key': process.env.ANTHROPIC_API_KEY,
            'anthropic-version': '2023-06-01'
        },
        body: JSON.stringify({
            model: 'claude-3-5-sonnet-20241022',
            messages,
            tools,
            max_tokens: 4096,
            stream
        })
    });

    return response;
}
```

### Python + FastAPI + WebSocket

```python
from fastapi import FastAPI, WebSocket
import anthropic
import json

app = FastAPI()

@app.websocket("/llm/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    client = anthropic.AsyncAnthropic()

    try:
        while True:
            data = await websocket.receive_text()
            message = json.loads(data)

            if message['type'] == 'CHAT_REQUEST':
                await handle_chat_request(websocket, message, client)
            elif message['type'] == 'TOOL_RESULT':
                await handle_tool_result(websocket, message, client)
            elif message['type'] == 'PING':
                await websocket.send_json({
                    'type': 'PONG',
                    'request_id': message['request_id']
                })

    except Exception as e:
        print(f"Error: {e}")
    finally:
        await websocket.close()

async def handle_chat_request(websocket: WebSocket, request: dict, client):
    request_id = request['request_id']
    data = request['data']

    if data.get('stream'):
        # 流式响应
        async with client.messages.stream(
            model="claude-3-5-sonnet-20241022",
            messages=data['messages'],
            tools=data.get('tools', []),
            max_tokens=data.get('max_tokens', 4096)
        ) as stream:
            async for event in stream:
                if event.type == 'content_block_delta':
                    if event.delta.type == 'text_delta':
                        await websocket.send_json({
                            'type': 'TEXT_DELTA',
                            'request_id': request_id,
                            'data': {'text': event.delta.text}
                        })

        # 发送完整响应
        final_message = await stream.get_final_message()
        await websocket.send_json({
            'type': 'CHAT_RESPONSE',
            'request_id': request_id,
            'data': {
                'content': final_message.content[0].text if final_message.content else '',
                'stop_reason': final_message.stop_reason
            }
        })
    else:
        # 非流式响应
        response = await client.messages.create(
            model="claude-3-5-sonnet-20241022",
            messages=data['messages'],
            tools=data.get('tools', []),
            max_tokens=data.get('max_tokens', 4096)
        )

        tool_calls = [
            {'id': block.id, 'name': block.name, 'input': block.input}
            for block in response.content if block.type == 'tool_use'
        ]

        await websocket.send_json({
            'type': 'CHAT_RESPONSE',
            'request_id': request_id,
            'data': {
                'content': response.content[0].text if response.content else '',
                'tool_calls': tool_calls,
                'stop_reason': response.stop_reason,
                'usage': {
                    'input_tokens': response.usage.input_tokens,
                    'output_tokens': response.usage.output_tokens
                }
            }
        })
```

## 配置与优化

### 连接配置

```java
OkHttpClient httpClient = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)      // 连接超时
    .readTimeout(0, TimeUnit.SECONDS)          // 读取超时（0 = 无限制，适合长连接）
    .writeTimeout(10, TimeUnit.SECONDS)        // 写入超时
    .pingInterval(30, TimeUnit.SECONDS)        // OkHttp 自动 ping
    .build();

WebSocketLLMProvider provider = new WebSocketLLMProvider(
    "ws://localhost:8080/llm/ws",
    "claude-3-5-sonnet-20241022",
    httpClient
);
```

### 心跳配置

默认心跳间隔：30秒
自动重连延迟：5秒

可以通过修改源码调整：
```java
private final int heartbeatInterval = 30; // seconds
private final int reconnectDelay = 5;     // seconds
```

### 错误处理

```java
provider.connect()
    .exceptionally(error -> {
        System.err.println("连接失败: " + error.getMessage());
        // 处理连接错误
        return null;
    });

provider.completeAsync(messages, options)
    .exceptionally(error -> {
        if (error.getCause() instanceof IOException) {
            System.err.println("网络错误，尝试重连...");
            // 自动重连逻辑
        }
        return null;
    });
```

## 性能考虑

### 优势

1. **低延迟** - WebSocket 长连接，无需每次建立 HTTP 连接
2. **实时性** - 双向通信，服务器可主动推送
3. **高效** - 相比 HTTP，header 开销更小
4. **并发** - 一个连接支持多个并发请求（通过 request_id 区分）

### 性能对比

| 特性 | HTTP + SSE | WebSocket |
|------|-----------|-----------|
| 连接方式 | 每次请求新连接 | 长连接复用 |
| 延迟 | ~100ms (建连) | ~5ms |
| Header 开销 | ~500 bytes/请求 | ~2 bytes/消息 |
| 双向通信 | 单向 (SSE) | 双向 |
| 并发请求 | 需要多个连接 | 单连接多请求 |

### 最佳实践

1. **连接池** - 如果需要高并发，创建连接池
   ```java
   List<WebSocketLLMProvider> pool = new ArrayList<>();
   for (int i = 0; i < 10; i++) {
       WebSocketLLMProvider provider = new WebSocketLLMProvider(url, model);
       provider.connect().get();
       pool.add(provider);
   }
   ```

2. **请求路由** - 使用 Round-Robin 或负载均衡
   ```java
   AtomicInteger counter = new AtomicInteger(0);
   WebSocketLLMProvider provider = pool.get(
       counter.getAndIncrement() % pool.size()
   );
   ```

3. **优雅关闭**
   ```java
   Runtime.getRuntime().addShutdownHook(new Thread(() -> {
       provider.shutdown();
   }));
   ```

## 总结

WebSocketLLMProvider 提供了**生产级的 WebSocket LLM 通信方案**：

✅ **基于 OkHttp** - 轻量、可靠、社区支持好
✅ **异步非阻塞** - CompletableFuture 异步模型
✅ **自动重连** - 断线自动恢复
✅ **Claude Skills** - 完整支持 function calling
✅ **流式响应** - 实时逐 token 输出
✅ **易于集成** - 实现 LLMProvider 接口

适用场景：
- 🎯 需要低延迟实时对话
- 🎯 服务器需要主动推送消息
- 🎯 高并发场景（连接复用）
- 🎯 长时间对话（连接保持）
