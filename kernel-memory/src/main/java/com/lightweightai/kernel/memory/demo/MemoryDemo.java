package com.lightweightai.kernel.memory.demo;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.file.SessionTranscript;
import com.lightweightai.kernel.memory.model.SearchResult;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import com.lightweightai.kernel.memory.queue.LaneQueueManager;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Interactive demo of the OpenClaw-style memory system.
 *
 * Run: mvn exec:java -pl kernel-memory -Dexec.mainClass="com.lightweightai.kernel.memory.demo.MemoryDemo"
 */
public class MemoryDemo {

    private final FileMemoryManager memory;
    private final LaneQueueManager lanes;
    private final MemoryToolkit toolkit;
    private SessionTranscript currentSession;

    public MemoryDemo(Path agentRoot) {
        this.memory = new FileMemoryManager(agentRoot, "demo-agent", new MockEmbeddingProvider(128));
        this.lanes = new LaneQueueManager();
        this.toolkit = MemoryToolkit.create(memory);
        this.currentSession = memory.createSession("interactive");
    }

    public void run() {
        printBanner();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\n> ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;

                if (input.startsWith("/")) {
                    if (!handleCommand(input)) break;
                } else {
                    handleMessage(input);
                }
            }
        } finally {
            memory.close();
            lanes.close();
        }
    }

    private boolean handleCommand(String cmd) {
        String[] parts = cmd.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "/help" -> printHelp();
            case "/search" -> doSearch(arg);
            case "/remember" -> doRemember(arg);
            case "/forget" -> doForget(arg);
            case "/status" -> showStatus();
            case "/history" -> showHistory();
            case "/sessions" -> listSessions();
            case "/new" -> newSession(arg);
            case "/quit", "/exit" -> {
                System.out.println("再见！");
                return false;
            }
            default -> System.out.println("未知命令。输入 /help 查看帮助。");
        }
        return true;
    }

    private void handleMessage(String message) {
        // Record user message
        currentSession.append(TranscriptEntry.userMessage(message));

        // Search for context
        var results = memory.search(message);

        if (!results.isEmpty()) {
            System.out.println("\n[回忆起相关记忆...]");
            for (int i = 0; i < Math.min(2, results.size()); i++) {
                SearchResult r = results.get(i);
                System.out.printf("  · %s (相关度: %.2f)%n",
                    truncate(r.getSnippet(), 50), r.getScore());
            }
        }

        // Simulate response
        String response = generateResponse(message, results);
        currentSession.append(TranscriptEntry.assistantMessage(response));

        System.out.println("\n助手: " + response);
    }

    private String generateResponse(String message, List<SearchResult> context) {
        // Simple rule-based responses for demo
        if (message.contains("你好") || message.contains("嗨")) {
            return "你好！有什么想聊的吗？";
        } else if (message.contains("记住") || message.contains("记得")) {
            return "好的，我会记住的。";
        } else if (message.contains("谢谢")) {
            return "不客气，随时可以找我聊天~";
        } else if (!context.isEmpty()) {
            return "根据我的记忆，我们之前聊过类似的话题。你想继续聊这个吗？";
        } else {
            return "我在听，请继续说...";
        }
    }

    private void doSearch(String query) {
        if (query.isEmpty()) {
            System.out.println("用法: /search <查询内容>");
            return;
        }

        var result = toolkit.getSearchTool().execute(Map.of("query", query, "top_k", 5));
        System.out.println(result.toText());
    }

    private void doRemember(String content) {
        if (content.isEmpty()) {
            System.out.println("用法: /remember <要记住的内容>");
            return;
        }

        var result = toolkit.getWriteTool().execute(Map.of(
            "content", content,
            "type", "durable",
            "section", "用户笔记"
        ));
        System.out.println(result.toText());
    }

    private void doForget(String content) {
        System.out.println("记忆清除功能暂未实现。记忆文件位于: " + memory.getAgentRoot());
    }

    private void showStatus() {
        var stats = memory.getIndexStats();
        System.out.println("\n=== 系统状态 ===");
        System.out.println("Agent ID: " + memory.getAgentId());
        System.out.println("Agent 目录: " + memory.getAgentRoot());
        System.out.println("当前会话: " + currentSession.getSessionId());
        System.out.println("BM25 索引块: " + stats.bm25ChunkCount());
        System.out.println("向量索引块: " + stats.vectorChunkCount());
        System.out.println("向量维度: " + stats.embeddingDimensions());
        System.out.println("会话数量: " + memory.listSessions().size());
    }

    private void showHistory() {
        var entries = currentSession.readAll();
        if (entries.isEmpty()) {
            System.out.println("当前会话没有历史记录。");
            return;
        }

        System.out.println("\n=== 会话历史 ===");
        for (var entry : entries) {
            String role = entry.getRole();
            String content = entry.getContent();
            if (role != null && content != null) {
                System.out.printf("[%s] %s%n", role, truncate(content, 60));
            }
        }
    }

    private void listSessions() {
        var sessions = memory.listSessions();
        if (sessions.isEmpty()) {
            System.out.println("没有历史会话。");
            return;
        }

        System.out.println("\n=== 历史会话 ===");
        for (String s : sessions) {
            System.out.println("  · " + s);
        }
    }

    private void newSession(String slug) {
        if (slug.isEmpty()) {
            currentSession = memory.createSession();
        } else {
            currentSession = memory.createSession(slug);
        }
        System.out.println("新会话已创建: " + currentSession.getSessionId());
    }

    private void printBanner() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║     🦞 OpenClaw-Style Memory System Demo 🦞           ║");
        System.out.println("║                                                       ║");
        System.out.println("║  三层记忆: Ephemeral + Durable + Session              ║");
        System.out.println("║  混合搜索: BM25 (关键词) + Vector (语义)              ║");
        System.out.println("║  串行队列: Lane Queue 防止会话竞争                    ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("输入消息开始对话，或输入 /help 查看命令。");
    }

    private void printHelp() {
        System.out.println("\n=== 可用命令 ===");
        System.out.println("/search <查询>    - 搜索记忆");
        System.out.println("/remember <内容>  - 保存到长期记忆");
        System.out.println("/status           - 查看系统状态");
        System.out.println("/history          - 查看会话历史");
        System.out.println("/sessions         - 列出所有会话");
        System.out.println("/new [名称]       - 创建新会话");
        System.out.println("/quit             - 退出");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", " ");
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    public static void main(String[] args) {
        Path agentRoot = args.length > 0
            ? Path.of(args[0])
            : Path.of(System.getProperty("user.home"), ".lightweightai", "agents", "demo");

        new MemoryDemo(agentRoot).run();
    }
}
