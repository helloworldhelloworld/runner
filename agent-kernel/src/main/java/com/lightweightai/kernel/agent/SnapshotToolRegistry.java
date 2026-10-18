package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ConfigurationContent;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import reactor.core.publisher.Flux;

import java.util.*;

/** Fixed tool bindings with live revocation delegated to the source registry. */
final class SnapshotToolRegistry extends ToolRegistry {
    private final ToolRegistry source;
    private final Map<String, FrozenTool> bindings;

    SnapshotToolRegistry(ToolRegistry source, ToolRegistry catalog, DeviceContext device) {
        this.source = source;
        Map<String, FrozenTool> selected = new TreeMap<>();
        for (Tool tool : catalog.getEnabled()) {
            Tool resolved = tool instanceof DispatchingTool dispatching ? dispatching.resolve(device) : tool;
            if (resolved != null) selected.put(tool.getName(), resolved instanceof FrozenTool frozen
                    ? frozen : new FrozenTool(resolved, tool));
        }
        this.bindings = Collections.unmodifiableMap(selected);
    }
    @Override public Optional<Tool> get(String name) { return Optional.ofNullable(bindings.get(name)); }
    @Override public boolean has(String name) { return bindings.containsKey(name); }
    @Override public List<Tool> getAll() { return List.copyOf(bindings.values()); }
    @Override public List<Tool> getEnabled() { return getAll().stream().filter(t -> isEnabled(t.getName())).toList(); }
    @Override public boolean isEnabled(String name) { return has(name) && source.isEnabled(name); }
    @Override public List<Tool> getByCategory(String category) {
        return getAll().stream().filter(t -> category.equals(((ToolMetadata) t).getCategory())).toList();
    }
    @Override public List<Tool> getByTag(String tag) {
        return getAll().stream().filter(t -> ((ToolMetadata) t).getTags().contains(tag)).toList();
    }
    @Override public int size() { return bindings.size(); }
    @Override public int enabledCount() { return getEnabled().size(); }
    @Override public Tool resolveForExecution(String name, DeviceContext device) {
        FrozenTool binding = bindings.get(name);
        return binding == null ? null : binding.delegate;
    }
    @Override public boolean isExecutionAllowed(String name, Tool resolved, Map<String, Object> args, ToolExecutionContext context) {
        return source.isExecutionAllowed(name, resolved, args, context);
    }

    static final class FrozenTool implements Tool, ToolMetadata {
        final Tool delegate;
        final Tool catalogTool;
        private final String name;
        private final String description;
        private final ToolSchema schema;
        private final RiskLevel risk;
        private final String version;
        private final String category;
        private final List<String> tags;
        FrozenTool(Tool delegate, Tool catalogTool) {
            this.delegate = delegate;
            this.catalogTool = catalogTool;
            name = delegate.getName();
            description = delegate.getDescription();
            schema = new ToolSchema(ConfigurationContent.map(delegate.getSchema().toMap()));
            risk = delegate.riskLevel();
            category = delegate instanceof ToolMetadata metadata ? metadata.getCategory() : "default";
            tags = delegate instanceof ToolMetadata metadata ? List.copyOf(metadata.getTags()) : List.of();
            version = delegate instanceof ToolMetadata metadata ? Objects.toString(metadata.getVersion(), "unspecified") : "unspecified";
        }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public ToolSchema getSchema() { return schema; }
        public RiskLevel riskLevel() { return risk; }
        public String getVersion() { return version; }
        public String getCategory() { return category; }
        public List<String> getTags() { return tags; }
        public boolean isAutoExecute() { return delegate.isAutoExecute(); }
        public ToolResult execute(Map<String, Object> args) { return delegate.execute(args); }
        public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) { return delegate.executeReactive(args); }
    }
}
