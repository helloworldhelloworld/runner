package com.lightweightai.safety;

import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters diagnostic or medical content from AI responses.
 *
 * AI assistants must not make clinical diagnoses. This filter detects
 * and replaces overly diagnostic statements with softer language.
 */
public class ContentFilter {

    private static final Logger logger = LoggerFactory.getLogger(ContentFilter.class);

    private static final List<Pattern> DIAGNOSTIC_PATTERNS = List.of(
        Pattern.compile("你(患有|得了|有|是)(抑郁症|焦虑症|双相|躁郁症|精神分裂|边缘型人格障碍|PTSD)"),
        Pattern.compile("根据你的描述.{0,20}(诊断|判断)为"),
        Pattern.compile("这是(抑郁|焦虑|双相|躁郁|精神疾病)的(症状|表现|信号)"),
        Pattern.compile("建议(立即|马上|尽快)(就医|看医生|就诊|服药|用药)")
    );

    private static final String SOFT_REPLACEMENT =
        "你描述的这些感受值得被认真对待。我建议你考虑和专业的心理咨询师或医生聊聊，他们能给你更专业的支持。";

    /**
     * Filter diagnostic content from an AI-generated response.
     *
     * @param response the AI response text
     * @return filtered response text
     */
    public String filter(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }

        String filtered = response;
        boolean modified = false;

        for (Pattern pattern : DIAGNOSTIC_PATTERNS) {
            if (pattern.matcher(filtered).find()) {
                filtered = pattern.matcher(filtered).replaceAll(SOFT_REPLACEMENT);
                modified = true;
            }
        }

        if (modified) {
            logger.info("Filtered diagnostic content from AI response");
        }

        return filtered;
    }
}
