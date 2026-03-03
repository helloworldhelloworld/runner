package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolScanner;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo 2: SPI 自动扫描 + 注解方式发现
 *
 * 演示 ToolScanner 如何通过 Java SPI 自动发现工具：
 *
 *   扫描链路：
 *   ┌─ Tool SPI ──────────────────────────────────────────────┐
 *   │  META-INF/services/com.lightweightai.kernel.agent.Tool  │
 *   │  → ServiceLoader 加载 implements Tool 的类              │
 *   └────────────────────────────────────────────────────────┘
 *   ┌─ ToolSource SPI ───────────────────────────────────────────────┐
 *   │  META-INF/services/com.lightweightai.kernel.agent.ToolSource  │
 *   │  → ServiceLoader 加载 implements ToolSource 的类               │
 *   │  → ToolSource.discoverTools() 返回批量工具                     │
 *   │  → 支持注解方式：内部调用 AnnotatedToolScanner.scan()           │
 *   └───────────────────────────────────────────────────────────────┘
 *
 *   ToolScanner 同时扫描两种 SPI，合并结果。
 *
 * agent-tools 模块的 SPI 配置示例：
 *   文件：agent-tools/src/main/resources/META-INF/services/
 *         com.lightweightai.kernel.agent.ToolSource
 *   内容：com.lightweightai.tools.AgentToolsSource
 *
 *   AgentToolsSource.discoverTools() 内部：
 *     List&lt;Tool&gt; tools = new ArrayList&lt;&gt;();
 *     tools.addAll(AnnotatedToolScanner.scan(new MathTools()));
 *     tools.addAll(AnnotatedToolScanner.scan(new TimeTools()));
 *     tools.addAll(AnnotatedToolScanner.scan(new WebTools()));
 *     return tools;
 *
 * 运行方式：
 *   mvn exec:java -pl agent-demo -Dexec.mainClass=com.lightweightai.demo.SpiScanDemo
 */
public class SpiScanDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Demo 2: SPI Auto-Scan");
        System.out.println("========================================\n");

        // ── 方式一：手动扫描 ──
        System.out.println("[1] ToolScanner.scan() — 发现所有 SPI 工具：");
        List<Tool> allTools = ToolScanner.scan();
        allTools.forEach(t -> System.out.println("  - " + t.getName() + ": " + t.getDescription()));
        System.out.println("  Total: " + allTools.size() + "\n");

        // ── 方式二：按分类扫描 ──
        System.out.println("[2] ToolScanner.scanByCategory(\"math\")：");
        List<Tool> mathTools = ToolScanner.scanByCategory("math");
        mathTools.forEach(t -> System.out.println("  - " + t.getName()));

        // ── 方式三：按标签扫描 ──
        System.out.println("\n[3] ToolScanner.scanByTag(\"calculation\")：");
        List<Tool> calcTools = ToolScanner.scanByTag("calculation");
        calcTools.forEach(t -> System.out.println("  - " + t.getName()));

        // ── 方式四：一行代码扫描+注册 ──
        System.out.println("\n[4] registry.scanAndRegister() — 一行完成扫描+注册：");
        ToolRegistry registry = new ToolRegistry();
        int count = registry.scanAndRegister();
        System.out.println("  Registered " + count + " tools\n");

        // ── 方式五：带过滤器扫描 ──
        System.out.println("[5] scanAndRegister with filter (exclude web_search)：");
        ToolRegistry filtered = new ToolRegistry();
        int filteredCount = filtered.scanAndRegister(
            tool -> !tool.getName().equals("web_search")
        );
        System.out.println("  Registered " + filteredCount + " tools (web_search excluded)\n");

        // ── 验证：实际执行扫描到的工具 ──
        System.out.println("[6] Execute scanned tools：");
        ToolExecutor executor = new ToolExecutor(registry);

        Map<String, Object> addArgs = new HashMap<>();
        addArgs.put("a", 100);
        addArgs.put("b", 200);
        ToolResult addResult = executor.executeToolCall(
            new ToolCall("demo_1", "add", addArgs));
        System.out.println("  add(100, 200) = " + addResult.getContent());

        Map<String, Object> mulArgs = new HashMap<>();
        mulArgs.put("a", 7);
        mulArgs.put("b", 8);
        ToolResult mulResult = executor.executeToolCall(
            new ToolCall("demo_2", "multiply", mulArgs));
        System.out.println("  multiply(7, 8) = " + mulResult.getContent());

        ToolResult timeResult = executor.executeToolCall(
            new ToolCall("demo_3", "get_time", Collections.emptyMap()));
        System.out.println("  get_time() = " + timeResult.getContent());
    }
}
