package com.lightweightai.kernel.agent.directive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientTool 注册表。
 */
public class DirectiveRegistry {

    private final Map<String, DirectiveDescriptor> directives = new ConcurrentHashMap<>();

    public void register(DirectiveDescriptor descriptor) {
        directives.put(descriptor.toToolName(), descriptor);
    }

    public void unregister(String toolName) {
        directives.remove(toolName);
    }

    public boolean has(String toolName) {
        return directives.containsKey(toolName);
    }

    public Optional<DirectiveDescriptor> get(String toolName) {
        return Optional.ofNullable(directives.get(toolName));
    }

    public List<DirectiveDescriptor> getAll() {
        return new ArrayList<>(directives.values());
    }

    public int size() {
        return directives.size();
    }

    public void clear() {
        directives.clear();
    }
}
