package com.lightweightai.kernel.testsupport;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用 AgentObserver：记录每一次 onPreToolUse / onPostToolUse 收到的 args 和 result。
 *
 * 目的：让 UT 能断言"调用和结果对上号"——CLAUDE.md UT 规则里的载荷型断言。
 * 避免各测试文件里手搓匿名 Observer 重复样板。
 */
public class CapturingAgentObserver implements AgentObserver {

    public record ToolInvocation(String name, Map<String, Object> args, ToolResult result) {}

    private final List<ToolInvocation> preCalls = new CopyOnWriteArrayList<>();
    private final List<ToolInvocation> postCalls = new CopyOnWriteArrayList<>();

    @Override
    public void onPreToolUse(String toolName, Map<String, Object> args) {
        preCalls.add(new ToolInvocation(toolName,
                args != null ? Map.copyOf(args) : Map.of(), null));
    }

    @Override
    public void onPostToolUse(String toolName, Map<String, Object> args, ToolResult result) {
        postCalls.add(new ToolInvocation(toolName,
                args != null ? Map.copyOf(args) : Map.of(), result));
    }

    public List<ToolInvocation> preCalls() { return List.copyOf(preCalls); }
    public List<ToolInvocation> postCalls() { return List.copyOf(postCalls); }
}
