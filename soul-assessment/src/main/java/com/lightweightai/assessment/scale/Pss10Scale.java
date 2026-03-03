package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * PSS-10: Perceived Stress Scale (10 items).
 * Score range: 0-40. Severity: 3档.
 * Note: items 4,5,7,8 (0-indexed) are reverse-scored.
 */
public class Pss10Scale {

    /** Indices of reverse-scored items (0-based). */
    public static final java.util.Set<Integer> REVERSED_ITEMS = new HashSet<>(Arrays.asList(3, 4, 6, 7));

    public static ScaleDefinition definition() {
        return new ScaleDefinition(
            ScaleType.PSS10,
            "压力知觉量表 (PSS-10)",
            "在过去一个月里，以下感受出现的频率是多少？",
            Arrays.asList(
                new ScaleDefinition.Question(0, "因为突发事件感到心烦意乱"),
                new ScaleDefinition.Question(1, "感到无法控制生活中重要的事情"),
                new ScaleDefinition.Question(2, "感到紧张和有压力"),
                new ScaleDefinition.Question(3, "成功处理了恼人的生活麻烦（反向）"),
                new ScaleDefinition.Question(4, "有效地应对生活中的重要变化（反向）"),
                new ScaleDefinition.Question(5, "对自己处理个人问题的能力感到有信心（反向）"),
                new ScaleDefinition.Question(6, "事情按照你的意愿进行（反向）"),
                new ScaleDefinition.Question(7, "能够控制生活中的烦恼（反向）"),
                new ScaleDefinition.Question(8, "因超出你控制范围的事而感到愤怒"),
                new ScaleDefinition.Question(9, "感到困难积累到无法克服的程度")
            ),
            Arrays.asList("从不", "几乎不", "有时", "经常", "总是"),
            Arrays.asList(0, 1, 2, 3, 4)
        );
    }

    /**
     * Interpret score into severity label.
     */
    public static String severity(int score) {
        if (score <= 13) return "低压力";
        if (score <= 26) return "中等压力";
        return "高压力";
    }
}
