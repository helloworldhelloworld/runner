package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.ScaleType;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Generates an AI interpretation report for an assessment result.
 * Uses the injected LLMProvider (optional — falls back to a template report if null).
 */
public class AssessmentReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentReportGenerator.class);

    private final LLMProvider llmProvider;

    public AssessmentReportGenerator(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    /**
     * Generate a warm, non-diagnostic AI report for the given assessment result.
     */
    public String generate(AssessmentResult result) {
        if (llmProvider == null) {
            return templateReport(result);
        }

        String prompt = buildPrompt(result);
        try {
            List<ConversationMessage> messages = Arrays.asList(
                ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.SYSTEM)
                    .textContent("你是一位温柔、专业的心理健康顾问助手。请用支持性、非诊断性的语言解读心理测评结果，给予情感支持和建议。不要做出临床诊断，不要推荐具体药物。")
                    .build(),
                ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent(prompt)
                    .build()
            );

            LLMOptions options = LLMOptions.builder()
                .maxTokens(500)
                .temperature(0.7)
                .build();

            LLMResponse response = llmProvider.complete(messages, options);
            return response.getMessage().getTextContent();
        } catch (Exception e) {
            logger.warn("LLM report generation failed, using template", e);
            return templateReport(result);
        }
    }

    private String buildPrompt(AssessmentResult result) {
        return String.format(
            "用户完成了%s测评，总分 %d 分，评估结果为「%s」。\n\n" +
            "请用温暖、鼓励的语气（200字以内）：\n" +
            "1. 简单解释这个分数的含义\n" +
            "2. 给予情感支持\n" +
            "3. 提供 2-3 个可以尝试的自我照顾建议\n" +
            "4. 如果分数偏高，温和地建议考虑寻求专业帮助\n\n" +
            "注意：不要做出临床诊断，不要推荐具体药物，语气要像朋友一样温暖。",
            result.getScaleType().getDisplayName(),
            result.getTotalScore(),
            result.getSeverity()
        );
    }

    private String templateReport(AssessmentResult result) {
        ScaleType type = result.getScaleType();
        int score = result.getTotalScore();
        String severity = result.getSeverity();

        StringBuilder sb = new StringBuilder();
        sb.append("感谢你完成了").append(type.getDisplayName()).append("测评。\n\n");
        sb.append("你的得分是 ").append(score).append(" 分，结果显示「").append(severity).append("」。\n\n");

        if (score <= getThreshold(type, 0)) {
            sb.append("你目前的状态看起来相对良好，继续保持日常的健康习惯，照顾好自己。😊");
        } else if (score <= getThreshold(type, 1)) {
            sb.append("你可能正在经历一些困难，这是很正常的。尝试保证充足的睡眠、适量运动，和信任的人多聊聊天。如果感觉持续不好转，可以考虑和专业心理咨询师交流。");
        } else {
            sb.append("你目前可能承受着比较大的压力或情绪困扰，我想让你知道你不是一个人。建议认真考虑和专业的心理咨询师或医生谈谈，他们能给你更有针对性的支持。你愿意寻求帮助，本身就是很勇敢的事。❤️");
        }

        return sb.toString();
    }

    private int getThreshold(ScaleType type, int level) {
        if (type == ScaleType.PHQ9) {
            return level == 0 ? 4 : 9;
        } else if (type == ScaleType.GAD7) {
            return level == 0 ? 4 : 9;
        } else if (type == ScaleType.PSS10) {
            return level == 0 ? 13 : 26;
        }
        return level == 0 ? 4 : 9;
    }
}
