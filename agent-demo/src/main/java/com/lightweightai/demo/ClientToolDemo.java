package com.lightweightai.demo;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.ClientToolWrapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ClientToolCallData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ClientToolResultData;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Demo 4: Client-Side Tool（端侧工具 — 在 App 上执行，结果回传云端）
 *
 * 端侧工具是指需要在用户设备（手机 App / 浏览器）上执行的工具，
 * 例如拍照、GPS 定位、签名采集等需要调用设备能力的操作。
 *
 * <b>核心设计：声明式 + 自动路由</b>
 *
 * 端侧工具和服务端工具使用完全相同的注册方式 —— {@code @ToolFunction} 注解。
 * 唯一区别是 {@code clientSide = true}。框架自动完成路由：
 *
 * <pre>
 * 声明:  @ToolFunction(clientSide = true)
 *          ↓ AnnotatedToolScanner.scan()
 *        AnnotatedToolWrapper (isClientSide() == true)
 *          ↓ registry.registerObject()
 *        和服务端工具一样注册到 ToolRegistry
 *
 * 调用:  ToolExecutor.executeToolCall(toolCall, context)
 *          ↓ 检测 tool.isClientSide()
 *          ├─ false → tool.execute(args)              [服务端本地执行]
 *          └─ true  → context.getClientDispatcher()
 *                       .dispatch(callId, name, args)  [派发到 App]
 *
 * 传输:  Server ──WS: CLIENT_TOOL_CALL──→ App 执行设备能力
 *        Server ←──WS: CLIENT_TOOL_RESULT── App 回传结果
 * </pre>
 *
 * 演示五部分：
 *   [A] 端侧工具声明 — @ToolFunction(clientSide = true)，和服务端工具一样注册
 *   [B] 模拟调度器 — 纯本地模拟 App 端行为（无需真实 WebSocket）
 *   [C] 透明路由 — 同一个 ToolExecutor，同一套代码，自动区分
 *   [D] ToolCallingLoop 集成 — LLM 自动触发端侧工具调用
 *   [E] 超时与错误处理 — App 未响应、用户拒绝授权
 *   [F] WebSocket 协议格式 — 真实场景中 Server↔App 的消息格式
 *
 * 运行方式：
 *   mvn exec:java -pl agent-demo -Dexec.mainClass=com.lightweightai.demo.ClientToolDemo
 */
public class ClientToolDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Demo 4: Client-Side Tool");
        System.out.println("  端侧工具 — 在 App 上执行，结果回传云端");
        System.out.println("========================================\n");

        demoRegistration();
        demoTransparentRouting();
        demoToolCallingLoop();
        demoTimeoutAndError();
        demoWebSocketProtocol();
    }

    // ================================================================
    //  [A] 端侧工具声明 — @ToolFunction(clientSide = true)
    //
    //  和服务端工具使用完全相同的注解方式声明：
    //    @ToolFunction(name = "take_photo", description = "...", clientSide = true)
    //
    //  注册方式也完全一样：
    //    registry.registerObject(new DeviceTools());
    //
    //  区别仅在 clientSide = true。AnnotatedToolWrapper 读取此属性，
    //  ToolExecutor 据此决定走 dispatch（App 执行）还是 execute（本地执行）。
    //
    //  方法体不会被调用 —— ToolExecutor 在调用 execute() 之前就拦截了。
    // ================================================================

    /**
     * 端侧工具声明 — 需要在 App 上执行的工具
     *
     * <pre>
     * 自动路由链路（方法体不会被调用）:
     *
     *   AnnotatedToolScanner.scan(new DeviceTools())
     *     → AnnotatedToolWrapper (isClientSide() == true)
     *     → registry.registerObject() 正常注册
     *
     *   ToolExecutor.executeToolCall(toolCall, context)
     *     → isClientSideTool(tool) == true
     *     → executeOnClient()
     *     → context.getClientDispatcher().dispatch()
     *     → 通过 WebSocket 派发到 App
     *     → 方法体永远不执行（被拦截了）
     * </pre>
     */
    public static class DeviceTools {

        @ToolFunction(
            name = "take_photo",
            description = "拍照并返回图片URL，用于记录用户当前状态或环境",
            clientSide = true,          // ← 标记为端侧工具
            autoExecute = false,        // ← 端侧工具需要用户确认
            category = "device",
            tags = {"camera", "photo"}
        )
        public String takePhoto(
            @ToolParam(name = "camera", description = "相机方向: front(前置) 或 back(后置)", required = true)
            String camera
        ) {
            // 此方法体不会被调用 — ToolExecutor 检测 isClientSide() 后走 dispatch 路径
            throw new UnsupportedOperationException("Client-side tool: executed on device, not server");
        }

        @ToolFunction(
            name = "get_location",
            description = "获取用户当前 GPS 位置（经纬度和城市名）",
            clientSide = true,
            autoExecute = false,
            category = "device",
            tags = {"gps", "location"}
        )
        public String getLocation() {
            throw new UnsupportedOperationException("Client-side tool: executed on device, not server");
        }

        @ToolFunction(
            name = "capture_signature",
            description = "采集用户手写签名，返回签名图片",
            clientSide = true,
            autoExecute = false,
            category = "device",
            tags = {"signature", "input"}
        )
        public String captureSignature(
            @ToolParam(name = "prompt_text", description = "签名提示语") String promptText
        ) {
            throw new UnsupportedOperationException("Client-side tool: executed on device, not server");
        }
    }

    /**
     * 服务端工具（对比用）— 在云端直接执行
     */
    public static class ServerSideTools {

        @ToolFunction(
            name = "format_date",
            description = "格式化日期字符串",
            category = "utility",
            tags = {"date", "format"},
            readOnly = true
        )
        public String formatDate(
            @ToolParam(name = "date", description = "日期 (yyyy-MM-dd)", required = true) String date,
            @ToolParam(name = "format", description = "目标格式") String format
        ) {
            if (format != null && format.contains("年")) {
                String[] parts = date.split("-");
                return parts[0] + "年" + parts[1] + "月" + parts[2] + "日";
            }
            return date;
        }

        @ToolFunction(
            name = "analyze_mood",
            description = "分析文本中的情绪（服务端 AI 分析）",
            category = "ai",
            tags = {"mood", "analysis"},
            readOnly = true
        )
        public String analyzeMood(
            @ToolParam(name = "text", description = "待分析的文本", required = true) String text
        ) {
            if (text.contains("开心") || text.contains("happy")) {
                return "{\"mood\": \"positive\", \"score\": 0.85, \"label\": \"开心\"}";
            }
            return "{\"mood\": \"neutral\", \"score\": 0.5, \"label\": \"平静\"}";
        }
    }

    // ================================================================
    //  [A] + [B] 注册演示
    // ================================================================

    static void demoRegistration() {
        System.out.println("--- [A][B] 端侧工具注册 — @ToolFunction(clientSide = true) ---\n");

        // 注册方式和服务端工具完全一样！
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new DeviceTools());     // 端侧工具
        registry.registerObject(new ServerSideTools()); // 服务端工具

        System.out.println("[Registry] " + registry.enabledCount() + " 个工具（端侧 + 服务端混合）:\n");

        System.out.println("  注册代码（和服务端工具完全相同）:");
        System.out.println("    registry.registerObject(new DeviceTools());     // 端侧");
        System.out.println("    registry.registerObject(new ServerSideTools()); // 服务端");
        System.out.println();

        for (Tool tool : registry.getEnabled()) {
            boolean clientSide = tool instanceof ToolMetadata meta && meta.isClientSide();
            String location = clientSide ? "端侧 (App 执行)" : "服务端 (本地执行)";
            System.out.println("  - " + tool.getName());
            System.out.println("      描述: " + tool.getDescription());
            System.out.println("      执行位置: " + location);
            System.out.println("      isClientSide(): " + clientSide);
        }

        System.out.println("\n  ✓ 端侧工具只需加 @ToolFunction(clientSide = true)");
        System.out.println("  ✓ 注册方式、调用方式与服务端工具完全相同");
        System.out.println("  ✓ 框架根据 isClientSide() 自动路由，无需手动包装\n");
    }

    // ================================================================
    //  [C] 透明路由 — ToolExecutor 自动区分服务端/端侧
    //
    //  关键设计：调用方代码完全相同！
    //
    //  ToolExecutor.executeToolCall(toolCall, context):
    //    1. 查询 ToolRegistry 获取 tool
    //    2. 检查 tool.isClientSide()
    //    3. 如果 true → context.getClientDispatcher().dispatch()
    //    4. 如果 false → tool.execute(args)
    //
    //  调用方只需传入 ToolExecutionContext（携带 dispatcher），
    //  ToolExecutor 自动决定路由方向。
    // ================================================================

    static void demoTransparentRouting() {
        System.out.println("--- [C] 透明路由：同一个 ToolExecutor，自动区分服务端/端侧 ---\n");

        SimulatedClientToolDispatcher dispatcher = new SimulatedClientToolDispatcher();

        // 注册：和服务端工具完全一样
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new DeviceTools());
        registry.registerObject(new ServerSideTools());

        // ToolExecutor + ToolExecutionContext（携带 dispatcher）
        ToolExecutor executor = new ToolExecutor(registry);
        ToolExecutionContext context = new ToolExecutionContext(dispatcher, 30_000);

        System.out.println("[ToolExecutor] 三次调用，代码完全相同：\n");

        // 调用服务端工具 — 走 tool.execute()
        ToolResult r1 = executor.executeToolCall(
            new ToolCall("1", "format_date", Map.of("date", "2026-03-09", "format", "yyyy年MM月dd日")),
            context);
        System.out.println("  format_date({date:\"2026-03-09\"})");
        System.out.println("    → " + r1.getContent());
        System.out.println("    ↑ 服务端执行: isClientSide()=false → tool.execute()\n");

        // 调用端侧工具 — 走 dispatcher.dispatch()
        ToolResult r2 = executor.executeToolCall(
            new ToolCall("2", "take_photo", Map.of("camera", "front")),
            context);
        System.out.println("  take_photo({camera:\"front\"})");
        System.out.println("    → " + r2.getContent());
        System.out.println("    ↑ 端侧执行: isClientSide()=true → dispatcher.dispatch() → App\n");

        // 调用端侧工具 — GPS 定位
        ToolResult r3 = executor.executeToolCall(
            new ToolCall("3", "get_location", Map.of()),
            context);
        System.out.println("  get_location({})");
        System.out.println("    → " + r3.getContent());
        System.out.println("    ↑ 端侧执行: isClientSide()=true → dispatcher.dispatch() → App\n");

        System.out.println("  ✓ 三次调用代码完全相同：executor.executeToolCall(toolCall, context)");
        System.out.println("  ✓ ToolExecutor 内部自动路由：");
        System.out.println("      isClientSide() ? dispatcher.dispatch() : tool.execute()");
        System.out.println("  ✓ 注解方法体（throw UnsupportedOperationException）永远不会被调用\n");
    }

    // ================================================================
    //  [D] ToolCallingLoop 集成 — LLM 触发端侧工具
    //
    //  Agent 场景：LLM 判断需要拍照/定位，自动调用端侧工具，
    //  等待 App 回传结果后继续生成回复。
    //
    //  关键：ToolCallingLoop 通过 builder().executionContext(ctx)
    //  传入 ToolExecutionContext，内部自动传递给 ToolExecutor。
    // ================================================================

    static void demoToolCallingLoop() {
        System.out.println("--- [D] ToolCallingLoop：LLM 自动触发端侧工具调用 ---\n");

        SimulatedClientToolDispatcher dispatcher = new SimulatedClientToolDispatcher();

        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new DeviceTools());
        registry.registerObject(new ServerSideTools());

        ToolExecutor executor = new ToolExecutor(registry);
        ToolExecutionContext context = new ToolExecutionContext(dispatcher, 30_000);

        // MockLLM: 模拟 LLM 先调用 take_photo，再根据结果生成回复
        LLMProvider mockLlm = new MockClientToolLLM();

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockLlm)
            .toolExecutor(executor)
            .executionContext(context)  // ← 传入 context，启用端侧工具路由
            .maxIterations(5)
            .build();

        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent("帮我拍一张照片记录今天的心情")
            .build());

        System.out.println("  用户: 帮我拍一张照片记录今天的心情\n");

        LLMResponse response = loop.executeWithTools(messages,
            LLMOptions.builder()
                .toolDefinitions(executor.getToolDefinitions())
                .build());

        System.out.println("  AI 最终回复: " + response.getMessage().getTextContent());
        System.out.println("\n  ✓ LLM 自动判断需要调用端侧工具 take_photo");
        System.out.println("  ✓ ToolCallingLoop 通过 ToolExecutionContext 路由到 App");
        System.out.println("  ✓ App 拍照上传后，LLM 拿到照片 URL 继续生成回复\n");
    }

    // ================================================================
    //  [E] 超时与错误处理
    //
    //  端侧工具的特殊挑战：
    //    - App 可能不响应（网络断开、App 被杀）→ 超时
    //    - 用户拒绝授权（如拒绝相机权限）→ 错误
    //
    //  这些由 ToolExecutor.executeOnClient() 统一处理：
    //    - 超时：dispatcher.dispatch().get(timeout) → TimeoutException
    //    - 错误：App 回传 ToolResult.error()
    //    - 断连：dispatcher future.completeExceptionally()
    // ================================================================

    static void demoTimeoutAndError() {
        System.out.println("--- [E] 超时与错误处理 ---\n");

        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry);

        // 场景 1: 超时 — App 未响应
        System.out.println("[场景 1] 超时 — App 未在指定时间内响应：\n");
        {
            ClientToolDispatcher slowDispatcher = (callId, toolName, args) ->
                new CompletableFuture<>(); // 永不 complete

            // ClientToolWrapper 也可以单独使用（带内嵌 dispatcher 的简便方式）
            ClientToolWrapper slowTool = new ClientToolWrapper(
                "take_photo", "拍照", ToolSchema.empty(), slowDispatcher, 2_000);
            ToolResult result = slowTool.execute(Map.of());

            System.out.println("  take_photo() → " + result.getContent());
            System.out.println("  isError: " + result.isError());
            System.out.println("  ↑ App 未响应，2 秒后超时\n");
        }

        // 场景 2: 用户拒绝授权
        System.out.println("[场景 2] 错误 — 用户拒绝授权：\n");
        {
            ClientToolDispatcher errorDispatcher = (callId, toolName, args) ->
                CompletableFuture.completedFuture(ToolResult.error("用户拒绝了相机权限，无法拍照"));

            // 通过 ToolExecutor 路由（@ToolFunction 声明的端侧工具 + 自定义 dispatcher）
            registry.registerObject(new DeviceTools());
            ToolExecutionContext ctx = new ToolExecutionContext(errorDispatcher, 30_000);

            ToolResult result = executor.executeToolCall(
                new ToolCall("err1", "take_photo", Map.of("camera", "front")), ctx);

            System.out.println("  take_photo({camera:\"front\"}) → " + result.getContent());
            System.out.println("  isError: " + result.isError());
            System.out.println("  ↑ App 回传错误，LLM 据此调整回复\n");
        }

        // 场景 3: WebSocket 断开
        System.out.println("[场景 3] 连接断开 — WebSocket session 关闭：\n");
        {
            ClientToolDispatcher disconnected = (callId, toolName, args) -> {
                CompletableFuture<ToolResult> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("WebSocket session closed"));
                return f;
            };
            ToolExecutionContext ctx = new ToolExecutionContext(disconnected, 30_000);

            ToolResult result = executor.executeToolCall(
                new ToolCall("err2", "get_location", Map.of()), ctx);

            System.out.println("  get_location() → " + result.getContent());
            System.out.println("  isError: " + result.isError());
            System.out.println("  ↑ 连接断开，框架自动捕获异常返回错误\n");
        }
    }

    // ================================================================
    //  [F] WebSocket 协议格式
    //
    //  真实场景中，Server 和 App 通过 WebSocket 交换 JSON 消息。
    //  这里展示消息格式，供 App 端（iOS/Android/Web）开发参考。
    // ================================================================

    static void demoWebSocketProtocol() {
        System.out.println("--- [F] WebSocket 协议格式（Server ↔ App）---\n");

        // Server → App: CLIENT_TOOL_CALL
        System.out.println("[1] Server → App（CLIENT_TOOL_CALL）:\n");

        ClientToolCallData callData = new ClientToolCallData(
            "call-uuid-12345", "take_photo", Map.of("camera", "front"), 30_000L);
        WebSocketMessage callMsg = WebSocketMessage.clientToolCall("call-uuid-12345", callData);

        try {
            System.out.println("  " + formatJson(callMsg.toJson()));
        } catch (Exception e) {
            System.out.println("  (序列化失败: " + e.getMessage() + ")");
        }

        System.out.println("\n  App 收到后:");
        System.out.println("    1. 解析 tool_name + arguments");
        System.out.println("    2. 调用设备能力（如相机 API）");
        System.out.println("    3. 回传结果 ↓\n");

        // App → Server: CLIENT_TOOL_RESULT（成功）
        System.out.println("[2] App → Server（成功）:\n");

        ClientToolResultData successData = new ClientToolResultData(
            "call-uuid-12345", "https://cdn.example.com/photos/mood.jpg", false);

        try {
            System.out.println("  " + formatJson(
                WebSocketMessage.clientToolResult("call-uuid-12345", successData).toJson()));
        } catch (Exception e) {
            System.out.println("  (序列化失败)");
        }

        // App → Server: CLIENT_TOOL_RESULT（失败）
        System.out.println("\n[3] App → Server（失败）:\n");

        ClientToolResultData errorData = new ClientToolResultData(
            "call-uuid-12345", "用户拒绝了相机权限", true);

        try {
            System.out.println("  " + formatJson(
                WebSocketMessage.clientToolResult("call-uuid-12345", errorData).toJson()));
        } catch (Exception e) {
            System.out.println("  (序列化失败)");
        }

        // App 端接入指南
        System.out.println("\n[4] App 端接入伪代码:\n");
        System.out.println("  websocket.onMessage(msg -> {");
        System.out.println("      if (msg.type == \"CLIENT_TOOL_CALL\") {");
        System.out.println("          var callId = msg.data.call_id;");
        System.out.println("          var tool   = msg.data.tool_name;");
        System.out.println("          var args   = msg.data.arguments;");
        System.out.println("          switch (tool) {");
        System.out.println("              case \"take_photo\":");
        System.out.println("                  camera.takePicture(args.camera, (url, err) ->");
        System.out.println("                      ws.send({type:\"client_tool_result\",");
        System.out.println("                               callId, content: url, isError: !!err}));");
        System.out.println("                  break;");
        System.out.println("              case \"get_location\":");
        System.out.println("                  gps.getCurrentPosition((pos, err) -> ws.send({...}));");
        System.out.println("                  break;");
        System.out.println("          }");
        System.out.println("      }");
        System.out.println("  });");

        // 架构图
        System.out.println("\n[5] 完整架构:\n");
        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.println("  │  Cloud (Server)                                          │");
        System.out.println("  │                                                          │");
        System.out.println("  │  @ToolFunction(clientSide = true)                        │");
        System.out.println("  │       ↓ AnnotatedToolScanner                             │");
        System.out.println("  │  AnnotatedToolWrapper (isClientSide() == true)           │");
        System.out.println("  │       ↓ ToolRegistry.registerObject()                    │");
        System.out.println("  │  ToolExecutor.executeToolCall(toolCall, context)          │");
        System.out.println("  │       ├─ isClientSide() == false → tool.execute()        │");
        System.out.println("  │       └─ isClientSide() == true  ─────────────────┐      │");
        System.out.println("  │           context.getClientDispatcher().dispatch() │      │");
        System.out.println("  │                                                   │      │");
        System.out.println("  │  WebSocketClientToolDispatcher                    │      │");
        System.out.println("  │       ├─ pendingCalls[callId] = Future            │      │");
        System.out.println("  │       └─ session.send(CLIENT_TOOL_CALL) ──────────┤      │");
        System.out.println("  └───────────────────────────────────────────────────│──────┘");
        System.out.println("                       WebSocket                     │");
        System.out.println("  ┌───────────────────────────────────────────────────│──────┐");
        System.out.println("  │  Device (App)                                    ↓      │");
        System.out.println("  │       ├─ 解析 tool_name + arguments                      │");
        System.out.println("  │       ├─ 调用设备能力 (Camera / GPS / Signature)          │");
        System.out.println("  │       └─ 回传 CLIENT_TOOL_RESULT ────────────────↑       │");
        System.out.println("  └──────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ================================================================
    //  辅助：格式化 JSON
    // ================================================================

    private static String formatJson(String json) {
        return json
            .replace(",\"", ",\n           \"")
            .replace("{\"", "{\n           \"")
            .replace("}", "\n          }");
    }

    // ================================================================
    //  [B] 模拟客户端调度器 — 模拟 App 端行为
    //
    //  真实场景中由 WebSocketClientToolDispatcher 实现：
    //    - afterConnectionEstablished() 创建 dispatcher（绑定 session）
    //    - dispatch() 通过 session.sendMessage() 推送到 App
    //    - App 回传时 completeCall() 完成 Future
    //
    //  这里在本地模拟 App 的执行逻辑和网络延迟。
    // ================================================================

    static class SimulatedClientToolDispatcher implements ClientToolDispatcher {

        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName,
                                                       Map<String, Object> args) {
            System.out.println("    [App] 收到端侧工具调用: " + toolName
                + " (callId=" + callId.substring(0, Math.min(8, callId.length())) + "...)");

            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(300); // 模拟 App 端处理延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                ToolResult result = switch (toolName) {
                    case "take_photo" -> {
                        String camera = (String) args.getOrDefault("camera", "back");
                        String url = "https://cdn.example.com/photos/" + callId.substring(0, 8)
                            + "_" + camera + ".jpg";
                        System.out.println("    [App] 相机(" + camera + ")拍照完成 → " + url);
                        yield ToolResult.success(url);
                    }
                    case "get_location" -> {
                        System.out.println("    [App] GPS 定位完成 → 上海市浦东新区");
                        yield ToolResult.success(
                            "{\"lat\":31.2304,\"lng\":121.4737,\"city\":\"上海\",\"address\":\"浦东新区陆家嘴\"}");
                    }
                    case "capture_signature" -> {
                        String prompt = (String) args.getOrDefault("prompt_text", "请签名");
                        System.out.println("    [App] 签名采集完成 (提示: " + prompt + ")");
                        yield ToolResult.success("data:image/png;base64,iVBORw0KGgoAAAANSUhE...");
                    }
                    default -> {
                        System.out.println("    [App] 未知工具: " + toolName);
                        yield ToolResult.error("App 不支持此工具: " + toolName);
                    }
                };

                System.out.println("    [App] 结果已回传 Server");
                return result;
            });
        }
    }

    // ================================================================
    //  Mock LLM — 模拟 LLM 调用端侧工具
    //
    //  第 1 轮：LLM 返回 tool_use: take_photo({camera: "front"})
    //  第 2 轮：LLM 拿到照片 URL，生成最终回复
    // ================================================================

    static class MockClientToolLLM implements LLMProvider {
        private int callCount = 0;

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;

            if (callCount == 1) {
                System.out.println("  [LLM] Round 1: 需要拍照 → tool_use: take_photo({camera:\"front\"})");
                ToolCall call = new ToolCall("tool_001", "take_photo", Map.of("camera", "front"));
                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(MessageRole.ASSISTANT)
                        .textContent("")
                        .metadata(Map.of("tool_calls", List.of(call)))
                        .build())
                    .toolCalls(List.of(call))
                    .build();
            }

            String photoUrl = messages.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .reduce((a, b) -> b)
                .map(ConversationMessage::getTextContent)
                .orElse("(未收到照片)");

            System.out.println("  [LLM] Round 2: 收到照片 → " + photoUrl);
            System.out.println("  [LLM] Round 2: 生成最终回复\n");

            return LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(MessageRole.ASSISTANT)
                    .textContent("已经帮你拍好照片了！照片链接: " + photoUrl
                        + " 看起来今天的你精神很好，记得保持好心情哦~")
                    .build())
                .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(
                List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options,
                StreamEventHandler handler) {
            return completeAsync(messages, options);
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "mock-client-tool"; }
    }
}
