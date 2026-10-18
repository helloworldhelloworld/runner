package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.DeviceContext;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.prompt.PromptSnapshot;
import com.lightweightai.kernel.prompt.PromptSource;

import java.util.*;

/** Configuration sources shared by one execution family; cancellation and authorization remain live. */
public final class ExecutionConfigurationScope {
    private final Map<PromptSource, PromptSnapshot> prompts = new IdentityHashMap<>();
    private final Map<ToolRegistry, Map<DeviceContext, ToolRegistry>> tools = new IdentityHashMap<>();

    public synchronized PromptSnapshot prompts(PromptSource source) {
        return prompts.computeIfAbsent(Objects.requireNonNull(source), s -> Objects.requireNonNull(s.snapshot()));
    }
    public synchronized ToolRegistry tools(ToolRegistry source, DeviceContext device) {
        DeviceContext selected = device == null ? DeviceContext.DEFAULT : device;
        Map<DeviceContext, ToolRegistry> devices = tools.computeIfAbsent(Objects.requireNonNull(source), s -> new HashMap<>());
        return devices.computeIfAbsent(selected, d -> source.snapshot(this, d));
    }
}
