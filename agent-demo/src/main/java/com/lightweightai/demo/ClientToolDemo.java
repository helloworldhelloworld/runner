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
import java.util.concurrent.TimeUnit;

/**
 * Demo 4: Client-Side Tool（端侧工具 — 在 App 上执行，结果回传云端）
 *
 * 端侧工具是指需要在用户设备（手机 App / 浏览器）上执行的工具，
 * 例如拍照、GPS 定位、签名采集等需要调用设备能力的操作。
 *
 * <pre>
 * 核心流程:
 *
 *   Cloud (Server)                                    Device (App)
 *   ─────────────                                    ────────────
 *   LLM 返回 tool_use: "take_photo"
 *        ↓
 *   ToolExecutor 检测 isClientSide() == true
 *        ↓
 *   ClientToolDispatcher.dispatch()
 *        ├─── WebSocket ──→  CLIENT_TOOL_CALL ──→  App 收到指令
 *        │                                          App 调用相机 API
 *        │                                          App 上传照片
 *        │    CLIENT_TOOL_RESULT  ←── WebSocket ←── App 回传结果
 *        ↓
 *   ToolResult 返回给 Agent Loop
 *        ↓
 *   LLM 拿到照片 URL，继续生成回复
 * </pre>
 *
 * 演示五部分：
 *   [A] 端侧工具注册 — ClientToolWrapper 定义需要在 App 执行的工具
 *   [B] 模拟调度器 — 纯本地模拟 App 端行为（无需真实 WebSocket）
 *   [C] 透明路由 — 同一个 ToolExecutor，本地工具和端侧工具代码完全相同
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
    //  [A] + [B] 端侧工具注册 + 模拟调度器
    //
    //  ClientToolWrapper 将工具标记为 isClientSide() = true，
    //  ToolExecutor 据此将调用路由到 ClientToolDispatcher（而非本地执行）。
    //
    //  真实场景中 dispatcher 是 WebSocketClientToolDispatcher，
    //  这里用 SimulatedClientToolDispatcher 模拟 App 端行为。
    // ================================================================

    static void demoRegistration() {
        System.out.println("--- [A][B] 端侧工具注册 + 模拟调度器 ---\n");

        // 创建模拟调度器（模拟 App 端行为）
        SimulatedClientToolDispatcher dispatcher = new SimulatedClientToolDispatcher();

        // 注册端侧工具（需要在 App 上执行的工具）
        ToolRegistry registry = new ToolRegistry();

        // 工具 1: 拍照 — App 调用相机，上传照片，返回 URL
        registry.register(new ClientToolWrapper(
            "take_photo",
            "拍照并返回图片URL，用于记录用户当前状态或环境",
            ToolSchema.withRequired(Map.of(
                "camera", Map.of("type", "string", "description", "相机方向: front(前置) 或 back(后置)",
                    "enum", List.of("front", "back"))
            ), "camera"),
            dispatcher,
            30_000  // 30 秒超时（用户需要时间拍照）
        ));

        // 工具 2: GPS 定位 — App 调用定位 API，返回经纬度
        registry.register(new ClientToolWrapper(
            "get_location",
            "获取用户当前 GPS 位置（经纬度和城市名）",
            ToolSchema.empty(),  // 无参数
            dispatcher,
            10_000  // 10 秒超时
        ));

        // 工具 3: 签名采集 — App 弹出签名板，用户手写签名
        registry.register(new ClientToolWrapper(
            "capture_signature",
            "采集用户手写签名，返回签名图片",
            ToolSchema.withProperties(Map.of(
                "prompt_text", Map.of("type", "string", "description", "签名提示语")
            )),
            dispatcher,
            60_000  // 60 秒超时（用户需要时间签名）
        ));

        // 同时注册本地工具（服务端执行）做对比
        registry.registerObject(new ServerSideTools());

        // 打印注册信息
        System.out.println("[Registry] " + registry.enabledCount() + " 个工具（端侧 + 服务端混合）:\n");
        for (Tool tool : registry.getEnabled()) {
            boolean clientSide = tool instanceof ToolMetadata meta && meta.isClientSide();
            String location = clientSide ? "端侧 (App)" : "服务端";
            String timeout = "";
            if (tool instanceof ClientToolWrapper ctw) {
                timeout = ", timeout=" + ctw.getTimeoutMs() + "ms";
            }
            System.out.println("  - " + tool.getName());
            System.out.println("      描述: " + tool.getDescription());
            System.out.println("      执行位置: " + location + timeout);
        }
        System.out.println();
    }

    // ================================================================
    //  [C] 透明路由 — ToolExecutor 自动区分服务端/端侧
    //
    //  关键设计：调用方代码完全相同！
    //  ToolExecutor 内部检查 tool.isClientSide()，
    //  自动决定是本地执行还是通过 dispatcher 派发到 App。
    // ================================================================

    static void demoTransparentRouting() {
        System.out.println("--- [C] 透明路由：同一个 ToolExecutor，自动区分服务端/端侧 ---\n");

        SimulatedClientToolDispatcher dispatcher = new SimulatedClientToolDispatcher();

        // 注册混合工具
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ClientToolWrapper("take_photo", "拍照",
            ToolSchema.withRequired(Map.of(
                "camera", Map.of("type", "string", "description", "相机方向")
            ), "camera"),
            dispatcher, 30_000));
        registry.register(new ClientToolWrapper("get_location", "获取GPS位置",
            ToolSchema.empty(), dispatcher, 10_000));
        registry.registerObject(new ServerSideTools());

        // ToolExecutor + ToolExecutionContext
        ToolExecutor executor = new ToolExecutor(registry);
        ToolExecutionContext context = new ToolExecutionContext(dispatcher, 30_000);

        System.out.println("[ToolExecutor] 三次调用，代码完全相同：\n");

        // 调用服务端工具 — 本地直接执行
        ToolResult r1 = executor.executeToolCall(
            new ToolCall("1", "format_date", Map.of("date", "2026-03-09", "format", "yyyy年MM月dd日")),
            context);
        System.out.println("  format_date({date:\"2026-03-09\"})");
        System.out.println("    → " + r1.getContent());
        System.out.println("    ↑ 服务端本地执行 (tool.execute())\n");

        // 调用端侧工具 — 通过 dispatcher 派发到 App
        ToolResult r2 = executor.executeToolCall(
            new ToolCall("2", "take_photo", Map.of("camera", "front")),
            context);
        System.out.println("  take_photo({camera:\"front\"})");
        System.out.println("    → " + r2.getContent());
        System.out.println("    ↑ 端侧执行 (dispatcher.dispatch() → App → 结果回传)\n");

        // 调用端侧工具 — GPS 定位
        ToolResult r3 = executor.executeToolCall(
            new ToolCall("3", "get_location", Map.of()),
            context);
        System.out.println("  get_location({})");
        System.out.println("    → " + r3.getContent());
        System.out.println("    ↑ 端侧执行 (dispatcher.dispatch() → App → 结果回传)\n");

        System.out.println("  ✓ 三次调用代码完全相同：executor.executeToolCall(toolCall, context)");
        System.out.println("  ✓ ToolExecutor 内部自动路由：isClientSide() ? dispatch() : execute()");
        System.out.println();
    }

    // ================================================================
    //  [D] ToolCallingLoop 集成 — LLM 触发端侧工具
    //
    //  Agent 场景：LLM 判断需要拍照/定位，自动调用端侧工具，
    //  等待 App 回传结果后继续生成回复。
    // ================================================================

    static void demoToolCallingLoop() {
        System.out.println("--- [D] ToolCallingLoop：LLM 自动触发端侧工具调用 ---\n");

        SimulatedClientToolDispatcher dispatcher = new SimulatedClientToolDispatcher();

        ToolRegistry registry = new ToolRegistry();
        registry.register(new ClientToolWrapper("take_photo", "拍照并返回图片URL",
            ToolSchema.withRequired(Map.of(
                "camera", Map.of("type", "string", "description", "相机方向")
            ), "camera"),
            dispatcher, 30_000));
        registry.register(new ClientToolWrapper("get_location", "获取当前GPS位置",
            ToolSchema.empty(), dispatcher, 10_000));
        registry.registerObject(new ServerSideTools());

        ToolExecutor executor = new ToolExecutor(registry);
        ToolExecutionContext context = new ToolExecutionContext(dispatcher, 30_000);

        // MockLLM: 模拟 LLM 先调用 take_photo，再根据结果生成回复
        LLMProvider mockLlm = new MockClientToolLLM();

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockLlm)
            .toolExecutor(executor)
            .executionContext(context)  // 关键：传入 context 启用端侧路由
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
        System.out.println("  ✓ App 拍照上传后，LLM 拿到照片 URL 继续生成回复");
        System.out.println();
    }

    // ================================================================
    //  [E] 超时与错误处理
    //
    //  端侧工具的特殊挑战：
    //    - App 可能不响应（网络断开、App 被杀）→ 超时
    //    - 用户拒绝授权（如拒绝相机权限）→ 错误
    // ================================================================

    static void demoTimeoutAndError() {
        System.out.println("--- [E] 超时与错误处理 ---\n");

        // 场景 1: 超时 — App 未响应
        System.out.println("[场景 1] 超时 — App 未在指定时间内响应：\n");

        ClientToolDispatcher slowDispatcher = (callId, toolName, args) -> {
            // 模拟 App 长时间无响应
            CompletableFuture<ToolResult> future = new CompletableFuture<>();
            // 故意不 complete，让 ClientToolWrapper 超时
            return future;
        };

        ClientToolWrapper slowTool = new ClientToolWrapper(
            "take_photo", "拍照", ToolSchema.empty(),
            slowDispatcher,
            2_000  // 2 秒超时（演示用，实际会更长）
        );

        ToolResult timeoutResult = slowTool.execute(Map.of());
        System.out.println("  take_photo() → " + timeoutResult.getContent());
        System.out.println("  isError: " + timeoutResult.isError());
        System.out.println("  ↑ App 未响应，2 秒后自动超时返回错误\n");

        // 场景 2: 用户拒绝授权
        System.out.println("[场景 2] 错误 — 用户拒绝授权：\n");

        ClientToolDispatcher errorDispatcher = (callId, toolName, args) ->
            CompletableFuture.completedFuture(
                ToolResult.error("用户拒绝了相机权限，无法拍照")
            );

        ClientToolWrapper deniedTool = new ClientToolWrapper(
            "take_photo", "拍照", ToolSchema.empty(),
            errorDispatcher
        );

        ToolResult errorResult = deniedTool.execute(Map.of());
        System.out.println("  take_photo() → " + errorResult.getContent());
        System.out.println("  isError: " + errorResult.isError());
        System.out.println("  ↑ App 回传错误，LLM 据此调整回复（如换一种方式记录心情）\n");

        // 场景 3: WebSocket 断开
        System.out.println("[场景 3] 连接断开 — WebSocket session 关闭：\n");

        ClientToolDispatcher disconnectedDispatcher = (callId, toolName, args) -> {
            CompletableFuture<ToolResult> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("WebSocket session closed"));
            return future;
        };

        ClientToolWrapper disconnectedTool = new ClientToolWrapper(
            "take_photo", "拍照", ToolSchema.empty(),
            disconnectedDispatcher
        );

        ToolResult disconnectedResult = disconnectedTool.execute(Map.of());
        System.out.println("  take_photo() → " + disconnectedResult.getContent());
        System.out.println("  isError: " + disconnectedResult.isError());
        System.out.println("  ↑ 连接断开，框架自动捕获异常并返回错误\n");
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
            "call-uuid-12345",
            "take_photo",
            Map.of("camera", "front"),
            30_000L
        );
        WebSocketMessage callMsg = WebSocketMessage.clientToolCall("call-uuid-12345", callData);

        try {
            String callJson = callMsg.toJson();
            System.out.println("  " + formatJson(callJson));
        } catch (Exception e) {
            System.out.println("  (序列化失败: " + e.getMessage() + ")");
        }

        System.out.println("\n  App 收到后执行:");
        System.out.println("    1. 解析 tool_name → \"take_photo\"");
        System.out.println("    2. 解析 arguments → {camera: \"front\"}");
        System.out.println("    3. 调用设备相机 API（前置摄像头）");
        System.out.println("    4. 上传照片到 CDN");
        System.out.println("    5. 回传结果 ↓\n");

        // App → Server: CLIENT_TOOL_RESULT（成功）
        System.out.println("[2] App → Server（CLIENT_TOOL_RESULT 成功）:\n");

        ClientToolResultData successData = new ClientToolResultData(
            "call-uuid-12345",
            "https://cdn.example.com/photos/user123/20260309_mood.jpg",
            false
        );
        WebSocketMessage successMsg = WebSocketMessage.clientToolResult("call-uuid-12345", successData);

        try {
            System.out.println("  " + formatJson(successMsg.toJson()));
        } catch (Exception e) {
            System.out.println("  (序列化失败)");
        }

        // App → Server: CLIENT_TOOL_RESULT（失败）
        System.out.println("\n[3] App → Server（CLIENT_TOOL_RESULT 失败）:\n");

        ClientToolResultData errorData = new ClientToolResultData(
            "call-uuid-12345",
            "用户拒绝了相机权限",
            true
        );
        WebSocketMessage errorMsg = WebSocketMessage.clientToolResult("call-uuid-12345", errorData);

        try {
            System.out.println("  " + formatJson(errorMsg.toJson()));
        } catch (Exception e) {
            System.out.println("  (序列化失败)");
        }

        // App 端接入指南
        System.out.println("\n[4] App 端接入指南:\n");
        System.out.println("  // iOS / Android / Web 端处理逻辑伪代码:");
        System.out.println("  websocket.onMessage(message -> {");
        System.out.println("      if (message.type == \"CLIENT_TOOL_CALL\") {");
        System.out.println("          String callId   = message.data.call_id;");
        System.out.println("          String toolName = message.data.tool_name;");
        System.out.println("          Map    args     = message.data.arguments;");
        System.out.println("          Long   timeout  = message.data.timeout_ms;");
        System.out.println();
        System.out.println("          switch (toolName) {");
        System.out.println("              case \"take_photo\":");
        System.out.println("                  camera.takePicture(args.camera, (url, err) -> {");
        System.out.println("                      websocket.send({");
        System.out.println("                          type: \"client_tool_result\",");
        System.out.println("                          callId: callId,");
        System.out.println("                          content: err ? err.message : url,");
        System.out.println("                          isError: err != null");
        System.out.println("                      });");
        System.out.println("                  });");
        System.out.println("                  break;");
        System.out.println("              case \"get_location\":");
        System.out.println("                  gps.getCurrentPosition((pos, err) -> {");
        System.out.println("                      websocket.send({...});");
        System.out.println("                  });");
        System.out.println("                  break;");
        System.out.println("          }");
        System.out.println("      }");
        System.out.println("  });");

        // 架构图
        System.out.println("\n[5] 端侧工具完整架构:\n");
        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.println("  │  Cloud (Server)                                          │");
        System.out.println("  │                                                          │");
        System.out.println("  │  AgentLoop / ToolCallingLoop                             │");
        System.out.println("  │       ↓ executeToolCall(toolCall, context)               │");
        System.out.println("  │  ToolExecutor                                            │");
        System.out.println("  │       ├─ isClientSide()?                                 │");
        System.out.println("  │       │   ├─ false → tool.execute(args)  [服务端本地]     │");
        System.out.println("  │       │   └─ true  → dispatcher.dispatch() ──────┐       │");
        System.out.println("  │       │                                          │       │");
        System.out.println("  │  WebSocketClientToolDispatcher                   │       │");
        System.out.println("  │       ├─ pendingCalls[callId] = Future           │       │");
        System.out.println("  │       ├─ session.send(CLIENT_TOOL_CALL) ─────────┤       │");
        System.out.println("  │       │                                          │       │");
        System.out.println("  └───────│──────────────────────────────────────────│───────┘");
        System.out.println("          │ WebSocket                               │");
        System.out.println("  ┌───────│──────────────────────────────────────────│───────┐");
        System.out.println("  │       ↓                                          ↑       │");
        System.out.println("  │  Device (App)                                            │");
        System.out.println("  │       ├─ 解析 tool_name + arguments                      │");
        System.out.println("  │       ├─ 调用设备能力 (Camera / GPS / Signature)          │");
        System.out.println("  │       └─ 回传 CLIENT_TOOL_RESULT ────────────────┘       │");
        System.out.println("  │                                                          │");
        System.out.println("  └──────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ================================================================
    //  辅助：格式化 JSON（简单缩进，演示用）
    // ================================================================

    private static String formatJson(String json) {
        // 简单格式化：在 , 和 { 后换行
        return json
            .replace(",\"", ",\n           \"")
            .replace("{\"", "{\n           \"")
            .replace("}", "\n          }");
    }

    // ================================================================
    //  模拟客户端调度器 — 模拟 App 端行为
    //
    //  真实场景中由 WebSocketClientToolDispatcher 实现，
    //  通过 WebSocket 推送 CLIENT_TOOL_CALL 到 App，
    //  App 执行后通过 WebSocket 回传 CLIENT_TOOL_RESULT。
    //
    //  这里在本地模拟 App 端的执行逻辑和网络延迟。
    // ================================================================

    static class SimulatedClientToolDispatcher implements ClientToolDispatcher {

        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName,
                                                       Map<String, Object> args) {
            System.out.println("    [App] 收到端侧工具调用: " + toolName
                + " (callId=" + callId.substring(0, 8) + "...)");

            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 模拟 App 端处理延迟（拍照/定位需要时间）
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                ToolResult result = switch (toolName) {
                    case "take_photo" -> {
                        String camera = (String) args.getOrDefault("camera", "back");
                        String url = "https://cdn.example.com/photos/" + callId.substring(0, 8)
                            + "_" + camera + ".jpg";
                        System.out.println("    [App] 相机(" + camera + ")拍照完成，已上传: " + url);
                        yield ToolResult.success(url);
                    }
                    case "get_location" -> {
                        String location = "{\"lat\": 31.2304, \"lng\": 121.4737, \"city\": \"上海\", \"address\": \"浦东新区陆家嘴\"}";
                        System.out.println("    [App] GPS 定位完成: 上海市浦东新区陆家嘴");
                        yield ToolResult.success(location);
                    }
                    case "capture_signature" -> {
                        String promptText = (String) args.getOrDefault("prompt_text", "请签名");
                        System.out.println("    [App] 签名采集完成 (提示: " + promptText + ")");
                        yield ToolResult.success("data:image/png;base64,iVBORw0KGgoAAAANSUhE...(签名图片数据)");
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
    //  服务端工具（对比用）
    // ================================================================

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
            // 简单模拟
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
            // 简单模拟
            if (text.contains("开心") || text.contains("happy") || text.contains("高兴")) {
                return "{\"mood\": \"positive\", \"score\": 0.85, \"label\": \"开心\"}";
            }
            return "{\"mood\": \"neutral\", \"score\": 0.5, \"label\": \"平静\"}";
        }
    }

    // ================================================================
    //  Mock LLM — 模拟 LLM 调用端侧工具
    //
    //  模拟场景：用户说"帮我拍一张照片记录心情"
    //    第 1 轮：LLM 返回 tool_use: take_photo({camera: "front"})
    //    第 2 轮：LLM 拿到照片 URL，生成最终回复
    // ================================================================

    static class MockClientToolLLM implements LLMProvider {
        private int callCount = 0;

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;

            if (callCount == 1) {
                // 第 1 轮：LLM 判断需要调用端侧工具 take_photo
                System.out.println("  [LLM] Round 1: 需要拍照 → tool_use: take_photo({camera: \"front\"})");
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

            // 第 2 轮：LLM 拿到照片 URL，生成最终回复
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
