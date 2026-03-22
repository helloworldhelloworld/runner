package com.lightweightai.kernel.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动注册 Provider — 用于需要构造参数的工具。
 *
 * 支持 addTool(Tool) 和 addAnnotated(Object) 两种注册方式，
 * stop() 时自动注销已注册的工具。
 */
public class ManualToolProvider implements ToolSourceProvider {

    private final List<Tool> tools = new ArrayList<>();
    private final List<Object> annotatedObjects = new ArrayList<>();

    public ManualToolProvider addTool(Tool tool) {
        tools.add(tool);
        return this;
    }

    public ManualToolProvider addAnnotated(Object obj) {
        annotatedObjects.add(obj);
        return this;
    }

    @Override
    public String sourceType() {
        return "manual";
    }

    @Override
    public void start(ToolRegistry registry) {
        tools.forEach(registry::register);
        annotatedObjects.forEach(registry::registerObject);
    }

    @Override
    public void stop(ToolRegistry registry) {
        tools.forEach(t -> registry.unregister(t.getName()));
    }
}
