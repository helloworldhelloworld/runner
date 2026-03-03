package com.lightweightai.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 远程服务端配置
 *
 * 支持 YAML/代码配置多个 MCP 服务端连接，结合 McpToolManager 使用：
 *
 * <pre>
 * // YAML 配置示例（需配合 Jackson/SnakeYAML 加载）：
 * // mcp:
 * //   servers:
 * //     weather:
 * //       command: "npx"
 * //       args: ["-y", "@weather/mcp-server"]
 * //       env:
 * //         API_KEY: "${WEATHER_API_KEY}"
 * //     filesystem:
 * //       command: "npx"
 * //       args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
 * //     remote-api:
 * //       transport: "sse"
 * //       url: "http://localhost:8080/sse"
 * //       enabled: false
 *
 * // 代码配置：
 * McpConfiguration config = new McpConfiguration();
 * config.addServer("weather", McpConfiguration.ServerConfig.stdio("npx", "-y", "@weather/mcp-server"));
 * config.addServer("db", McpConfiguration.ServerConfig.sse("http://localhost:3000/sse"));
 *
 * // 结合 McpToolManager 使用：
 * McpToolManager manager = McpToolManager.create()
 *     .fromConfig(config)
 *     .registerLocal(new MathTools())
 *     .build();
 * </pre>
 *
 * 风格对齐 {@link com.lightweightai.kernel.config.LLMConfiguration}
 */
public class McpConfiguration {

    private Map<String, ServerConfig> servers = new LinkedHashMap<>();

    public Map<String, ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerConfig> servers) {
        this.servers = servers;
    }

    public void addServer(String name, ServerConfig config) {
        this.servers.put(name, config);
    }

    /**
     * 获取所有已启用的服务端配置
     */
    public Map<String, ServerConfig> getEnabledServers() {
        Map<String, ServerConfig> enabled = new LinkedHashMap<>();
        for (Map.Entry<String, ServerConfig> entry : servers.entrySet()) {
            if (entry.getValue().isEnabled()) {
                enabled.put(entry.getKey(), entry.getValue());
            }
        }
        return enabled;
    }

    /**
     * 单个 MCP 服务端连接配置
     */
    public static class ServerConfig {

        /** 传输类型: "stdio" (默认) 或 "sse" */
        private String transport = "stdio";

        /** STDIO 传输：启动命令 */
        private String command;

        /** STDIO 传输：命令参数 */
        private List<String> args = new ArrayList<>();

        /** STDIO 传输：环境变量 */
        private Map<String, String> env = new HashMap<>();

        /** SSE 传输：服务端 URL */
        private String url;

        /** 是否启用此服务端 */
        private boolean enabled = true;

        /** 请求超时（秒） */
        private long timeoutSeconds = 30;

        public ServerConfig() {}

        // ==================== 快捷工厂方法 ====================

        /**
         * 创建 STDIO 传输配置
         *
         * @param command 启动命令
         * @param args    命令参数
         */
        public static ServerConfig stdio(String command, String... args) {
            ServerConfig config = new ServerConfig();
            config.transport = "stdio";
            config.command = command;
            config.args = List.of(args);
            return config;
        }

        /**
         * 创建 SSE 传输配置
         *
         * @param url 服务端 SSE 端点 URL
         */
        public static ServerConfig sse(String url) {
            ServerConfig config = new ServerConfig();
            config.transport = "sse";
            config.url = url;
            return config;
        }

        // ==================== Getters/Setters ====================

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        /**
         * 添加环境变量（链式调用）
         */
        public ServerConfig withEnv(String key, String value) {
            this.env.put(key, value);
            return this;
        }

        /**
         * 设置超时（链式调用）
         */
        public ServerConfig withTimeout(long seconds) {
            this.timeoutSeconds = seconds;
            return this;
        }

        @Override
        public String toString() {
            if ("sse".equalsIgnoreCase(transport)) {
                return "ServerConfig{transport=sse, url='" + url + "', enabled=" + enabled + "}";
            }
            return "ServerConfig{transport=stdio, command='" + command + "', args=" + args + ", enabled=" + enabled + "}";
        }
    }
}
