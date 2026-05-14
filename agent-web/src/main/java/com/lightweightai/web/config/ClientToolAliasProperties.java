package com.lightweightai.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClientTool 别名外部配置 — Spring Boot 类型化绑定。
 *
 * <p>将 {@code application.yml} 中的 {@code app.client-tool.aliases} 直接绑定为
 * {@code Map<String, List<String>>}，于启动期注入到
 * {@link com.lightweightai.kernel.agent.directive.ClientToolAliasRegistry}，
 * 让同一组 down/up 指令的 LLM 可见名可以按环境/产品差异化扩展，不需要改注解、不需要改工具实现。</p>
 *
 * <h2>YAML 配置示例</h2>
 * <pre>
 * app:
 *   client-tool:
 *     aliases:
 *       ExecOsApi:
 *         - ExecuteOSAPI
 *         - OsApiInvoke
 *       GetPosition:
 *         - Locate
 * </pre>
 *
 * <p>注解里写死的 {@code aliases = {...}} 与此处配置的别名会自动合并并去重，
 * 调用方（工具作者 / 注册方 / LLM）使用方式完全不变。</p>
 */
@ConfigurationProperties("app.client-tool")
public class ClientToolAliasProperties {

    private Map<String, List<String>> aliases = new LinkedHashMap<>();

    public Map<String, List<String>> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, List<String>> aliases) {
        this.aliases = aliases != null ? aliases : new LinkedHashMap<>();
    }

    public List<String> aliasesFor(String primaryToolName) {
        if (primaryToolName == null) {
            return Collections.emptyList();
        }
        List<String> v = aliases.get(primaryToolName);
        return v != null ? v : Collections.emptyList();
    }
}
