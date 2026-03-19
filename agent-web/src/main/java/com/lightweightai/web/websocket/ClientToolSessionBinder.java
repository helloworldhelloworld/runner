package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.GetPositionTool;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将“连接级 dispatcher”与“全局 ToolRegistry 中的端工具实例”绑定。
 *
 * 目标：把端工具注册与端云链路打通：连接建立即注册，可被 LLM 调用；连接关闭即注销并清理。
 */
public class ClientToolSessionBinder {

    private final ToolRegistry toolRegistry;
    private final Map<String, List<String>> sessionTools = new ConcurrentHashMap<>();
    private final Map<String, String> toolOwners = new ConcurrentHashMap<>();

    public ClientToolSessionBinder(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 绑定一个会话：注册该会话可用的端工具实例。
     */
    public void bind(String socketId, VertxWebSocketClientToolDispatcher dispatcher) {
        List<Tool> tools = List.of(new GetPositionTool(dispatcher));
        List<String> names = new ArrayList<>();

        for (Tool tool : tools) {
            String toolName = tool.getName();
            synchronized (this) {
                String owner = toolOwners.get(toolName);
                if (owner != null && !owner.equals(socketId)) {
                    // 已被其他连接占用，先释放旧 owner 的映射
                    unregisterOwnedTool(owner, toolName);
                }
                toolRegistry.register(tool);
                toolOwners.put(toolName, socketId);
            }
            names.add(toolName);
        }

        sessionTools.put(socketId, names);
    }

    /**
     * 解绑会话：只清理该会话拥有的工具，避免误删其他连接接管后的工具。
     */
    public synchronized void unbind(String socketId) {
        List<String> names = sessionTools.remove(socketId);
        if (names == null || names.isEmpty()) {
            return;
        }

        for (String toolName : names) {
            String owner = toolOwners.get(toolName);
            if (socketId.equals(owner)) {
                toolRegistry.unregister(toolName);
                toolOwners.remove(toolName);
            }
        }
    }

    private void unregisterOwnedTool(String ownerSocketId, String toolName) {
        List<String> owned = sessionTools.get(ownerSocketId);
        if (owned != null) {
            owned.remove(toolName);
        }
        toolRegistry.unregister(toolName);
        toolOwners.remove(toolName);
    }
}
