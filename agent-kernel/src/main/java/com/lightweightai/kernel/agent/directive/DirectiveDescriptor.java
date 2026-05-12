package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.agent.annotation.ClientTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 由 {@link ClientTool} 提取的标准 Directive 元信息。
 */
public class DirectiveDescriptor {

    private final String tool;
    private final List<String> aliases;
    private final String downAction;
    private final String upAction;
    private final String namespace;
    private final long timeoutMs;
    private final String version;

    public DirectiveDescriptor(String tool, String downAction, String upAction,
                               String namespace, long timeoutMs, String version) {
        this(tool, Collections.emptyList(), downAction, upAction, namespace, timeoutMs, version);
    }

    public DirectiveDescriptor(String tool, List<String> aliases, String downAction, String upAction,
                               String namespace, long timeoutMs, String version) {
        this.tool = requireText(tool, "tool cannot be blank");
        this.aliases = normalizeAliases(this.tool, aliases);
        this.downAction = requireText(downAction, "downAction cannot be blank");
        this.upAction = requireText(upAction, "upAction cannot be blank");
        this.namespace = requireText(namespace, "namespace cannot be blank");
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 60_000;
        this.version = requireText(version, "version cannot be blank");
    }

    public static DirectiveDescriptor from(ClientTool directive) {
        Objects.requireNonNull(directive, "directive cannot be null");
        return new DirectiveDescriptor(
            directive.toolName(),
            Arrays.asList(directive.aliases()),
            directive.downAction(),
            directive.upAction(),
            directive.namespace(),
            directive.timeoutMs(),
            directive.version()
        );
    }

    private static List<String> normalizeAliases(String primary, List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            String trimmed = alias.trim();
            if (trimmed.isEmpty() || trimmed.equals(primary)) {
                continue;
            }
            deduped.add(trimmed);
        }
        return Collections.unmodifiableList(new ArrayList<>(deduped));
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    public String getTool() {
        return tool;
    }

    /**
     * 返回所有 LLM 可见的工具名（主名 + 别名）。
     */
    public List<String> getAllToolNames() {
        if (aliases.isEmpty()) {
            return Collections.singletonList(tool);
        }
        List<String> all = new ArrayList<>(aliases.size() + 1);
        all.add(tool);
        all.addAll(aliases);
        return Collections.unmodifiableList(all);
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getDownAction() {
        return downAction;
    }

    public String getUpAction() {
        return upAction;
    }

    public String getNamespace() {
        return namespace;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public String getVersion() {
        return version;
    }

    public String toToolName() {
        return tool;
    }

    public String toDownlinkToolName() {
        return downAction;
    }
}
