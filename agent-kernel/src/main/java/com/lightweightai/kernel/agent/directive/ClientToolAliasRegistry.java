package com.lightweightai.kernel.agent.directive;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 全局 {@link ClientToolAliasSource} 持有者。
 *
 * <p>默认值为 {@link PropertiesClientToolAliasSource}（懒加载 classpath 上的
 * {@code client-tool-aliases.properties}）。应用启动时可调用
 * {@link #setSource(ClientToolAliasSource)} 注入自定义实现
 * （Spring `@Value`、Nacos、Apollo……）。</p>
 *
 * <p>{@link DirectiveDescriptor#from(com.lightweightai.kernel.agent.annotation.ClientTool)}
 * 在构建描述符时调用本类合并外部别名，对工具实现透明。</p>
 */
public final class ClientToolAliasRegistry {

    private static volatile ClientToolAliasSource source;

    private ClientToolAliasRegistry() {
    }

    /**
     * 注入运行时别名源，覆盖默认 Properties 源。
     */
    public static void setSource(ClientToolAliasSource newSource) {
        source = Objects.requireNonNull(newSource, "source cannot be null");
    }

    /**
     * 重置为默认行为：懒加载 classpath 上的 properties 配置。
     */
    public static void reset() {
        source = null;
    }

    /**
     * 查询某主工具名的外部别名。无配置时返回空列表。
     */
    public static List<String> aliasesFor(String primaryToolName) {
        if (primaryToolName == null) {
            return Collections.emptyList();
        }
        ClientToolAliasSource s = source;
        if (s == null) {
            s = defaultSource();
        }
        List<String> v = s.aliasesFor(primaryToolName);
        return v != null ? v : Collections.emptyList();
    }

    private static synchronized ClientToolAliasSource defaultSource() {
        if (source == null) {
            source = new PropertiesClientToolAliasSource();
        }
        return source;
    }
}
