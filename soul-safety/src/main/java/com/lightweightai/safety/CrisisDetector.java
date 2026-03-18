package com.lightweightai.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects crisis signals in user messages using keyword and pattern matching.
 *
 * Returns CRISIS level when high-risk keywords are found, prompting display
 * of emergency hotline information rather than continuing the AI conversation.
 */
public class CrisisDetector {

    private static final Logger logger = LoggerFactory.getLogger(CrisisDetector.class);

    private static final Set<String> HIGH_RISK_KEYWORDS = Set.of(
        "自杀", "自残", "割腕", "不想活", "不想活了", "活不下去", "活着没意思",
        "了结", "结束生命", "结束一切", "去死", "想死", "寻死", "轻生",
        "跳楼", "上吊", "服药", "吃药死", "安眠药", "死了算了",
        "不如死了", "宁可死", "死比活好", "死心", "没有活下去的理由",
        "没有活下去的意义", "再也不想见到明天", "不想醒来"
    );

    private static final List<Pattern> HIGH_RISK_PATTERNS = List.of(
        Pattern.compile("不想.*活"),
        Pattern.compile("活.*没.*意思"),
        Pattern.compile("结束.*生命"),
        Pattern.compile("了结.*自己"),
        Pattern.compile("死.*算了"),
        Pattern.compile("想.*去死"),
        Pattern.compile("自.*杀")
    );

    /**
     * Check the given message for crisis signals.
     *
     * @param message user message text
     * @return SafetyResult with SAFE or CRISIS level
     */
    public SafetyResult check(String message) {
        if (message == null || message.isBlank()) {
            return SafetyResult.safe();
        }

        List<String> matched = new ArrayList<>();

        for (String keyword : HIGH_RISK_KEYWORDS) {
            if (message.contains(keyword)) {
                matched.add(keyword);
            }
        }

        if (matched.isEmpty()) {
            for (Pattern pattern : HIGH_RISK_PATTERNS) {
                if (pattern.matcher(message).find()) {
                    matched.add(pattern.pattern());
                }
            }
        }

        if (!matched.isEmpty()) {
            logger.warn("Crisis signal detected in message, keywords: {}", matched);
            return SafetyResult.crisis(matched, CrisisResource.DEFAULTS);
        }

        return SafetyResult.safe();
    }
}
