package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;

import java.util.Arrays;
import java.util.List;

/**
 * PHQ-9: Patient Health Questionnaire for depression screening.
 * Score range: 0-27. Severity: 5档.
 */
public class Phq9Scale {

    public static ScaleDefinition definition() {
        return new ScaleDefinition(
            ScaleType.PHQ9,
            "抑郁自评量表 (PHQ-9)",
            "过去两周，以下问题出现的频率是多少？",
            Arrays.asList(
                new ScaleDefinition.Question(0, "做事缺乏兴趣或乐趣"),
                new ScaleDefinition.Question(1, "感到心情低落、沮丧或绝望"),
                new ScaleDefinition.Question(2, "入睡困难、睡不着或睡得太多"),
                new ScaleDefinition.Question(3, "感到疲倦或精力不足"),
                new ScaleDefinition.Question(4, "食欲不振或进食过多"),
                new ScaleDefinition.Question(5, "感到自己很糟糕，或觉得自己是失败者，让自己或家人失望"),
                new ScaleDefinition.Question(6, "对事物难以集中注意力，如看报纸或看电视"),
                new ScaleDefinition.Question(7, "行动或说话速度缓慢（让别人也注意到了），或相反，烦躁不安，动来动去"),
                new ScaleDefinition.Question(8, "有不如死掉或以某种方式伤害自己的念头")
            ),
            Arrays.asList("完全没有", "有几天", "超过一半天数", "几乎每天"),
            Arrays.asList(0, 1, 2, 3)
        );
    }

    /**
     * Interpret score into severity label.
     */
    public static String severity(int score) {
        if (score <= 4) return "无抑郁";
        if (score <= 9) return "轻度抑郁";
        if (score <= 14) return "中度抑郁";
        if (score <= 19) return "中重度抑郁";
        return "重度抑郁";
    }
}
