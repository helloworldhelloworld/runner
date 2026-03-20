package com.lightweightai.web.postprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 简易风控检查器 — 检查 LLM 输出中的风险内容
 *
 * <p>与 CrisisDetector（检查用户输入）不同，本检查器针对模型生成内容，
 * 防止 LLM 输出以下类型的有害内容：
 * <ul>
 *   <li>医疗诊断建议 — AI 不应给出具体诊断或用药方案</li>
 *   <li>违法犯罪指导 — 制毒、制爆、黑客攻击等</li>
 *   <li>金融投资建议 — 具体股票推荐、保证收益等</li>
 *   <li>个人信息泄露 — 身份证号、银行卡号等模式</li>
 *   <li>政治敏感内容 — 涉政言论</li>
 * </ul>
 *
 * <p>检查结果分三级：
 * <ul>
 *   <li>SAFE — 正常内容，透传</li>
 *   <li>WARNING — 轻微风险（如医疗类措辞），注入提示但继续输出</li>
 *   <li>BLOCKED — 高风险内容，立即终止流</li>
 * </ul>
 */
public class SimpleRiskChecker implements RiskControlProcessor.RiskChecker {

    private static final Logger logger = LoggerFactory.getLogger(SimpleRiskChecker.class);

    // ==================== BLOCKED 级别：高危关键词 ====================

    /** 违法犯罪指导 */
    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
        "制造炸弹", "制作炸药", "合成毒品", "制毒方法", "制造枪支",
        "入侵系统", "破解密码的方法", "如何攻击", "DDoS攻击教程",
        "如何洗钱", "偷税漏税方法", "伪造证件"
    );

    /** 高危正则模式 */
    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
        // 身份证号（18位）
        Pattern.compile("[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]"),
        // 银行卡号（16-19位连续数字）
        Pattern.compile("\\d{16,19}"),
        // 手机号（完整的11位手机号，前后不能是数字）
        Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
    );

    // ==================== WARNING 级别：需要提示但不终止 ====================

    /** 医疗诊断类 */
    private static final Set<String> WARNING_MEDICAL_KEYWORDS = Set.of(
        "你患有", "你得了", "诊断为", "确诊为",
        "建议服用", "需要吃药", "用药方案", "处方",
        "必须手术", "立即就医否则"
    );

    /** 金融建议类 */
    private static final Set<String> WARNING_FINANCIAL_KEYWORDS = Set.of(
        "保证收益", "稳赚不赔", "立即买入", "建议全仓",
        "一定涨", "一定跌", "投资回报率100%"
    );

    /** 政治敏感类 */
    private static final Set<String> WARNING_POLITICAL_KEYWORDS = Set.of(
        "推翻政府", "颠覆国家", "分裂国家"
    );

    /** 警告正则模式 */
    private static final List<Pattern> WARNING_PATTERNS = List.of(
        // 具体药品 + 剂量推荐
        Pattern.compile("(服用|口服|注射).{0,10}(mg|毫克|片|粒|支)"),
        // "你（一定/肯定/确实）有 XX 症/病"
        Pattern.compile("你(一定|肯定|确实|就是)(有|患|得).{0,8}(症|病|障碍)")
    );

    @Override
    public RiskControlProcessor.RiskResult check(String accumulatedText) {
        if (accumulatedText == null || accumulatedText.isEmpty()) {
            return RiskControlProcessor.RiskResult.safe();
        }

        // 1. 先检查 BLOCKED 级别
        for (String keyword : BLOCKED_KEYWORDS) {
            if (accumulatedText.contains(keyword)) {
                logger.warn("Risk BLOCKED: keyword [{}] found in output", keyword);
                return RiskControlProcessor.RiskResult.blocked("输出包含违规内容: " + keyword);
            }
        }

        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(accumulatedText).find()) {
                logger.warn("Risk BLOCKED: pattern [{}] matched in output", pattern.pattern());
                return RiskControlProcessor.RiskResult.blocked("输出包含敏感信息（疑似个人数据泄露）");
            }
        }

        // 2. 再检查 WARNING 级别
        for (String keyword : WARNING_MEDICAL_KEYWORDS) {
            if (accumulatedText.contains(keyword)) {
                logger.info("Risk WARNING: medical keyword [{}] found in output", keyword);
                return RiskControlProcessor.RiskResult.warning("AI 不提供医疗诊断，请咨询专业医生");
            }
        }

        for (String keyword : WARNING_FINANCIAL_KEYWORDS) {
            if (accumulatedText.contains(keyword)) {
                logger.info("Risk WARNING: financial keyword [{}] found in output", keyword);
                return RiskControlProcessor.RiskResult.warning("AI 不提供投资建议，请咨询专业理财顾问");
            }
        }

        for (String keyword : WARNING_POLITICAL_KEYWORDS) {
            if (accumulatedText.contains(keyword)) {
                logger.info("Risk WARNING: political keyword [{}] found in output", keyword);
                return RiskControlProcessor.RiskResult.warning("内容涉及敏感话题");
            }
        }

        for (Pattern pattern : WARNING_PATTERNS) {
            if (pattern.matcher(accumulatedText).find()) {
                logger.info("Risk WARNING: pattern [{}] matched in output", pattern.pattern());
                return RiskControlProcessor.RiskResult.warning("AI 不提供医疗诊断，请咨询专业医生");
            }
        }

        return RiskControlProcessor.RiskResult.safe();
    }
}
