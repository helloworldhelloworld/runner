package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.ClientToolWrapper;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.VersionRange;
import com.lightweightai.kernel.llm.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Directive ↔ Tool 桥接器
 *
 * 核心职责：
 * 1. 端侧声明能力 → 自动注册为 ClientToolWrapper 到 ToolRegistry（LLM 可见）
 * 2. ToolCall → Directive 格式转换（下发到端侧）
 * 3. DirectiveResult → ToolResult 格式转换（回传给框架）
 *
 * <pre>
 * 与现有组件的关系：
 * - 复用 ClientToolWrapper — 每个端侧能力都包装为一个 ClientToolWrapper
 * - 复用 ToolRegistry.register() / unregister() — 动态注册/注销
 * - 复用 ClientToolDispatcher — ClientToolWrapper 内部调用 dispatcher.dispatch()
 * - 不修改 ToolExecutor — 它看到 isClientSide()==true 的工具就走 client 路径
 * </pre>
 */
public class DirectiveToolBridge {

    private static final Logger logger = LoggerFactory.getLogger(DirectiveToolBridge.class);

    private final ToolRegistry toolRegistry;
    private final ClientToolDispatcher dispatcher;

    public DirectiveToolBridge(ToolRegistry toolRegistry, ClientToolDispatcher dispatcher) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
    }

    /**
     * 端侧上报 manifest 后自动调用 — 将能力注册为 Tool
     *
     * 每个 ClientCapability → 一个 ClientToolWrapper 注册到 ToolRegistry。
     * 工具名格式："{namespace}.{name}"，如 "Camera.TakePhoto"。
     *
     * @param manifest 端侧能力清单
     * @return 注册成功的工具名列表
     */
    public List<String> registerCapabilities(ClientManifest manifest) {
        Objects.requireNonNull(manifest, "manifest cannot be null");

        List<ClientCapability> capabilities = manifest.getCapabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            logger.warn("Empty manifest from client: {}", manifest.getClientType());
            return Collections.emptyList();
        }

        List<String> registered = new ArrayList<>();
        for (ClientCapability cap : capabilities) {
            String toolName = cap.toToolName();
            try {
                ToolSchema schema = buildSchema(cap.getInputSchema());
                ClientToolWrapper wrapper = new ClientToolWrapper(
                    toolName,
                    cap.getDescription(),
                    schema,
                    dispatcher,
                    cap.getDefaultTimeoutMs()
                );

                toolRegistry.register(wrapper, manifest.getClientType(),
                    VersionRange.exact(manifest.getClientVersion()));
                registered.add(toolName);
                logger.info("Registered directive tool: {} (from {} {})",
                    toolName, manifest.getClientType(), manifest.getClientVersion());

            } catch (Exception e) {
                logger.error("Failed to register directive tool: {}", toolName, e);
            }
        }

        logger.info("Registered {} directive tools from manifest (client={}, version={})",
            registered.size(), manifest.getClientType(), manifest.getClientVersion());
        return registered;
    }

    /**
     * 端侧断开时自动调用 — 注销端侧工具
     *
     * @param manifest 端侧能力清单
     */
    public void unregisterCapabilities(ClientManifest manifest) {
        if (manifest == null || manifest.getCapabilities() == null) {
            return;
        }

        for (ClientCapability cap : manifest.getCapabilities()) {
            String toolName = cap.toToolName();
            toolRegistry.unregisterDevice(toolName, manifest.getClientType());
            logger.info("Unregistered directive tool: {} (device={})", toolName, manifest.getClientType());
        }
    }

    /**
     * ToolCall → Directive 格式转换
     *
     * 从工具名解析 namespace 和 name（按 "." 分割）。
     * callId 直接映射为 directiveId。
     *
     * @param callId   调用 ID（将作为 directiveId）
     * @param toolName 工具名（"Namespace.Name" 格式）
     * @param args     参数
     * @return Directive 实例
     */
    public Directive toDirective(String callId, String toolName, Map<String, Object> args) {
        return Directive.fromToolName(callId, toolName, args, 0);
    }

    /**
     * DirectiveResult → ToolResult 格式转换
     *
     * 直接调用 directiveResult.toToolResult()。
     * toolUseId 不在这里处理 — ToolExecutor 的 withToolUseId() 统一绑定。
     */
    public ToolResult fromDirectiveResult(DirectiveResult result) {
        return result.toToolResult();
    }

    private ToolSchema buildSchema(Map<String, Object> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return ToolSchema.empty();
        }
        return new ToolSchema(inputSchema);
    }
}
