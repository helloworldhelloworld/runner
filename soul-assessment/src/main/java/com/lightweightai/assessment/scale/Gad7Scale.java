package com.lightweightai.assessment.scale;

import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import java.util.List;

/**
 * GAD-7: Generalized Anxiety Disorder 7-item scale.
 * Score range: 0-21. Severity: 4档.
 */
public class Gad7Scale {

    public static ScaleDefinition definition() {
        return new ScaleDefinition(
            ScaleType.GAD7,
            "焦虑自评量表 (GAD-7)",
            "过去两周，以下问题出现的频率是多少？",
            List.of(
                new ScaleDefinition.Question(0, "感到紧张、焦虑或急切"),
                new ScaleDefinition.Question(1, "不能停止担忧或控制担忧"),
                new ScaleDefinition.Question(2, "对各种各样的事情担忧过多"),
                new ScaleDefinition.Question(3, "很难放松下来"),
                new ScaleDefinition.Question(4, "由于不安而无法静坐"),
                new ScaleDefinition.Question(5, "变得容易烦恼或易怒"),
                new ScaleDefinition.Question(6, "感到有些可怕的事情可能要发生")
            ),
            List.of("完全没有", "有几天", "超过一半天数", "几乎每天"),
            List.of(0, 1, 2, 3)
        );
    }

    /**
     * Interpret score into severity label.
     */
    public static String severity(int score) {
        if (score <= 4) {
            return "无焦虑";
        }
        if (score <= 9) {
            return "轻度焦虑";
        }
        if (score <= 14) {
            return "中度焦虑";
        }
        return "重度焦虑";
    }
}
