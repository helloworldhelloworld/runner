package com.lightweightai.kernel.agent.directive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 从 classpath properties 文件读取 ClientTool 别名配置的 {@link ClientToolAliasSource}。
 *
 * <p>文件格式：</p>
 * <pre>
 * # &lt;primaryToolName&gt;=&lt;alias1&gt;,&lt;alias2&gt;,...
 * ExecOsApi=ExecuteOSAPI,OsApiInvoke
 * GetPosition=Locate
 * </pre>
 *
 * <p>默认从 classpath 上的 {@code client-tool-aliases.properties} 加载；
 * 文件不存在时返回空，不报错。</p>
 */
public class PropertiesClientToolAliasSource implements ClientToolAliasSource {

    public static final String DEFAULT_RESOURCE = "client-tool-aliases.properties";

    private static final Logger logger = LoggerFactory.getLogger(PropertiesClientToolAliasSource.class);

    private final Map<String, List<String>> aliases;

    public PropertiesClientToolAliasSource() {
        this(DEFAULT_RESOURCE);
    }

    public PropertiesClientToolAliasSource(String resourcePath) {
        this.aliases = loadFromClasspath(resourcePath);
    }

    public PropertiesClientToolAliasSource(Properties properties) {
        this.aliases = parse(properties);
    }

    @Override
    public List<String> aliasesFor(String primaryToolName) {
        if (primaryToolName == null) {
            return Collections.emptyList();
        }
        return aliases.getOrDefault(primaryToolName, Collections.emptyList());
    }

    /**
     * 暴露给测试 / 诊断使用，返回不可变快照。
     */
    public Map<String, List<String>> snapshot() {
        return Collections.unmodifiableMap(aliases);
    }

    private static Map<String, List<String>> loadFromClasspath(String resourcePath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = PropertiesClientToolAliasSource.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.debug("ClientTool alias config not found on classpath: {}", resourcePath);
                return Collections.emptyMap();
            }
            Properties props = new Properties();
            props.load(in);
            Map<String, List<String>> parsed = parse(props);
            logger.info("Loaded {} ClientTool alias mappings from {}", parsed.size(), resourcePath);
            return parsed;
        } catch (IOException e) {
            logger.warn("Failed to load ClientTool alias config from {}: {}", resourcePath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, List<String>> parse(Properties props) {
        if (props == null || props.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            String trimmedKey = key.trim();
            if (trimmedKey.isEmpty()) {
                continue;
            }
            String raw = props.getProperty(key);
            if (raw == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (String part : raw.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    values.add(t);
                }
            }
            if (!values.isEmpty()) {
                result.put(trimmedKey, Collections.unmodifiableList(values));
            }
        }
        return result;
    }
}
