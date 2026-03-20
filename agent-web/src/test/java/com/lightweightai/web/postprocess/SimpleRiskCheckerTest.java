package com.lightweightai.web.postprocess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimpleRiskChecker 风控检查器测试")
class SimpleRiskCheckerTest {

    private SimpleRiskChecker checker;

    @BeforeEach
    void setUp() {
        checker = new SimpleRiskChecker();
    }

    @Nested
    @DisplayName("安全内容")
    class SafeContent {

        @Test
        @DisplayName("普通对话内容应判定为安全")
        void normalContentShouldBeSafe() {
            var result = checker.check("今天天气真好，我们一起去公园散步吧");
            assertTrue(result.isSafe());
        }

        @Test
        @DisplayName("空内容应判定为安全")
        void emptyContentShouldBeSafe() {
            assertTrue(checker.check("").isSafe());
            assertTrue(checker.check(null).isSafe());
        }

        @Test
        @DisplayName("正常心理安慰内容应判定为安全")
        void normalComfortShouldBeSafe() {
            var result = checker.check("我理解你现在的感受，每个人都会经历这样的低谷期。建议你可以试试深呼吸放松一下。");
            assertTrue(result.isSafe());
        }
    }

    @Nested
    @DisplayName("BLOCKED 级别 — 高危内容")
    class BlockedContent {

        @Test
        @DisplayName("违法犯罪指导应被拦截")
        void illegalContentShouldBeBlocked() {
            var result = checker.check("下面教你如何制造炸弹，首先需要准备以下材料");
            assertTrue(result.isBlocked());
            assertNotNull(result.getReason());
        }

        @Test
        @DisplayName("黑客攻击教程应被拦截")
        void hackingContentShouldBeBlocked() {
            var result = checker.check("入侵系统的步骤如下");
            assertTrue(result.isBlocked());
        }

        @Test
        @DisplayName("疑似身份证号应被拦截")
        void idCardPatternShouldBeBlocked() {
            var result = checker.check("你的身份证号是110101199001011234");
            assertTrue(result.isBlocked());
            assertTrue(result.getReason().contains("敏感信息"));
        }

        @Test
        @DisplayName("疑似手机号应被拦截")
        void phoneNumberShouldBeBlocked() {
            var result = checker.check("你可以联系他，手机号是13812345678");
            assertTrue(result.isBlocked());
        }

        @Test
        @DisplayName("正常短数字不应被误判")
        void shortNumberShouldNotBeBlocked() {
            // 6位数字不够银行卡/身份证长度
            var result = checker.check("订单号是123456");
            assertTrue(result.isSafe());
        }
    }

    @Nested
    @DisplayName("WARNING 级别 — 需要提示的内容")
    class WarningContent {

        @Test
        @DisplayName("医疗诊断应触发警告")
        void medicalDiagnosisShouldWarn() {
            var result = checker.check("根据你的描述，你患有抑郁症");
            assertTrue(result.hasWarning());
            assertTrue(result.getReason().contains("医疗"));
        }

        @Test
        @DisplayName("用药建议应触发警告")
        void medicationAdviceShouldWarn() {
            var result = checker.check("建议服用阿莫西林胶囊");
            assertTrue(result.hasWarning());
        }

        @Test
        @DisplayName("药品+剂量模式应触发警告")
        void dosagePatternShouldWarn() {
            var result = checker.check("口服布洛芬400mg");
            assertTrue(result.hasWarning());
        }

        @Test
        @DisplayName("确诊断言模式应触发警告")
        void diagnosisAssertionShouldWarn() {
            var result = checker.check("你肯定有焦虑症");
            assertTrue(result.hasWarning());
        }

        @Test
        @DisplayName("金融建议应触发警告")
        void financialAdviceShouldWarn() {
            var result = checker.check("这只股票稳赚不赔，赶紧买入");
            assertTrue(result.hasWarning());
            assertTrue(result.getReason().contains("投资"));
        }

        @Test
        @DisplayName("政治敏感内容应触发警告")
        void politicalContentShouldWarn() {
            var result = checker.check("我们应该推翻政府");
            assertTrue(result.hasWarning());
        }
    }

    @Nested
    @DisplayName("优先级 — BLOCKED 优先于 WARNING")
    class Priority {

        @Test
        @DisplayName("同时包含 blocked 和 warning 内容时应返回 blocked")
        void blockedShouldTakePrecedence() {
            var result = checker.check("你患有抑郁症，这里教你制造炸弹");
            assertTrue(result.isBlocked());
        }
    }
}
