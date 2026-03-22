package com.lightweightai.plugin.example;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 示例插件工具：随机名言
 *
 * 演示无参数工具的实现方式。
 */
public class RandomQuoteTool implements Tool {

    private static final List<String> QUOTES = List.of(
        "The only way to do great work is to love what you do. — Steve Jobs",
        "In the middle of difficulty lies opportunity. — Albert Einstein",
        "The best time to plant a tree was 20 years ago. The second best time is now. — Chinese Proverb",
        "Stay hungry, stay foolish. — Steve Jobs",
        "The journey of a thousand miles begins with a single step. — Lao Tzu",
        "Life is what happens when you're busy making other plans. — John Lennon",
        "千里之行，始于足下。 — 老子",
        "学而不思则罔，思而不学则殆。 — 孔子",
        "天行健，君子以自强不息。 — 《周易》"
    );

    @Override
    public String getName() {
        return "random_quote";
    }

    @Override
    public String getDescription() {
        return "Get a random inspirational quote. Use this when the user needs encouragement or inspiration.";
    }

    @Override
    public ToolSchema getSchema() {
        return new ToolSchema(Map.of(
            "type", "object",
            "properties", Map.of()
        ));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        int index = ThreadLocalRandom.current().nextInt(QUOTES.size());
        return ToolResult.success("random_quote", QUOTES.get(index));
    }
}
