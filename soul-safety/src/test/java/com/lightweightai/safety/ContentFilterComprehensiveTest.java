package com.lightweightai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentFilter - 诊断内容过滤全覆盖")
class ContentFilterComprehensiveTest {

    private ContentFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ContentFilter();
    }

    @Nested
    @DisplayName("模式1: 你(患有|得了|有|是)(疾病)")
    class DiagnosisPattern {

        @Test
        @DisplayName("你患有躁郁症")
        void youHaveBipolarDisorder() {
            String r = filter.filter("你患有躁郁症，情况严重");
            assertFalse(r.contains("你患有躁郁症"));
            assertTrue(r.contains("专业"));
        }

        @Test
        @DisplayName("你得了精神分裂")
        void youGotSchizophrenia() {
            String r = filter.filter("你得了精神分裂，需要长期治疗");
            assertFalse(r.contains("你得了精神分裂"));
        }

        @Test
        @DisplayName("你有边缘型人格障碍")
        void youHaveBorderline() {
            String r = filter.filter("你有边缘型人格障碍的典型表现");
            assertFalse(r.contains("你有边缘型人格障碍"));
        }

        @Test
        @DisplayName("你是双相")
        void youAreBipolar() {
            String r = filter.filter("你是双相，需要情绪管理");
            assertFalse(r.contains("你是双相"));
        }

        @Test
        @DisplayName("你是PTSD")
        void youArePtsd() {
            String r = filter.filter("你是PTSD，需要专业干预");
            assertFalse(r.contains("你是PTSD"));
        }
    }

    @Nested
    @DisplayName("模式2: 根据描述诊断/判断为")
    class DescriptionDiagnosis {

        @Test
        @DisplayName("根据描述判断为")
        void judgedAs() {
            String r = filter.filter("根据你的描述，我判断为轻度焦虑");
            assertFalse(r.contains("判断为"));
        }

        @Test
        @DisplayName("根据描述，可以诊断为")
        void diagnosedAs() {
            String r = filter.filter("根据你的描述可以诊断为中度抑郁");
            assertFalse(r.contains("诊断为"));
        }

        @Test
        @DisplayName("描述和诊断之间有填充文字")
        void diagnosisWithFiller() {
            String r = filter.filter("根据你的描述，初步可以诊断为焦虑");
            assertFalse(r.contains("诊断为"));
        }
    }

    @Nested
    @DisplayName("模式3: 这是(疾病)的(症状|表现|信号)")
    class SymptomStatement {

        @Test
        @DisplayName("这是焦虑的表现")
        void anxietySymptom() {
            String r = filter.filter("这是焦虑的表现，要小心");
            assertFalse(r.contains("这是焦虑的表现"));
        }

        @Test
        @DisplayName("这是双相的信号")
        void bipolarSignal() {
            String r = filter.filter("这是双相的信号，你应该注意");
            assertFalse(r.contains("这是双相的信号"));
        }

        @Test
        @DisplayName("这是躁郁的症状")
        void maniaSymptom() {
            String r = filter.filter("这是躁郁的症状表现");
            assertFalse(r.contains("这是躁郁的症状"));
        }

        @Test
        @DisplayName("这是精神疾病的表现")
        void mentalIllnessSign() {
            String r = filter.filter("这是精神疾病的表现，不容忽视");
            assertFalse(r.contains("这是精神疾病的表现"));
        }
    }

    @Nested
    @DisplayName("模式4: 建议(立即|马上|尽快)(就医|看医生|就诊|服药|用药)")
    class UrgentMedicalAdvice {

        @Test
        @DisplayName("建议马上就诊")
        void suggestImmediateVisit() {
            String r = filter.filter("建议马上就诊，别耽误了");
            assertFalse(r.contains("建议马上就诊"));
        }

        @Test
        @DisplayName("建议尽快看医生")
        void suggestSeeDoctor() {
            String r = filter.filter("建议尽快看医生进行评估");
            assertFalse(r.contains("建议尽快看医生"));
        }

        @Test
        @DisplayName("建议立即用药")
        void suggestImmediateMedication() {
            String r = filter.filter("建议立即用药治疗");
            assertFalse(r.contains("建议立即用药"));
        }

        @Test
        @DisplayName("建议马上服药")
        void suggestTakeMedicineNow() {
            String r = filter.filter("建议马上服药控制病情");
            assertFalse(r.contains("建议马上服药"));
        }
    }

    @Nested
    @DisplayName("多模式同时匹配")
    class MultiplePatternMatches {

        @Test
        @DisplayName("一条回复触发三个不同模式")
        void threePatternsTrigger() {
            String response = "你患有抑郁症。这是焦虑的表现。建议立即就医。";
            String r = filter.filter(response);
            assertFalse(r.contains("你患有抑郁症"));
            assertFalse(r.contains("这是焦虑的表现"));
            assertFalse(r.contains("建议立即就医"));
            assertTrue(r.contains("专业"));
        }

        @Test
        @DisplayName("同一模式匹配两次")
        void samePatternTwice() {
            String response = "你患有抑郁症，同时你得了焦虑症";
            String r = filter.filter(response);
            assertFalse(r.contains("你患有抑郁症"));
            assertFalse(r.contains("你得了焦虑症"));
        }
    }

    @Nested
    @DisplayName("不应误过滤的安全内容")
    class SafeContent {

        @Test
        @DisplayName("建议+非紧急用语不过滤")
        void nonUrgentAdvice() {
            String response = "建议你可以考虑和专业人士聊聊";
            assertEquals(response, filter.filter(response));
        }

        @Test
        @DisplayName("他人患病描述不过滤(你字开头才匹配)")
        void thirdPersonDiagnosis() {
            String response = "他患有抑郁症，需要关怀";
            assertEquals(response, filter.filter(response));
        }

        @Test
        @DisplayName("一般情感支持回复")
        void generalEmotionalSupport() {
            String response = "我理解你正在经历困难，你的感受是有效的";
            assertEquals(response, filter.filter(response));
        }

        @Test
        @DisplayName("提及症状但不做诊断")
        void mentionSymptomWithoutDiagnosis() {
            String response = "失眠和食欲下降可能会让人不舒服";
            assertEquals(response, filter.filter(response));
        }
    }

    @Nested
    @DisplayName("替换文本验证")
    class ReplacementVerification {

        @Test
        @DisplayName("替换文本包含'专业的心理咨询师或医生'引导语")
        void replacementContainsGuidance() {
            String r = filter.filter("你患有抑郁症");
            assertTrue(r.contains("专业的心理咨询师或医生"));
        }

        @Test
        @DisplayName("替换文本保留原有非诊断内容")
        void preservesNonDiagnosticContent() {
            String r = filter.filter("你最近睡眠如何？你患有抑郁症。好好休息吧。");
            assertTrue(r.contains("你最近睡眠如何？"));
            assertTrue(r.contains("好好休息吧。"));
            assertFalse(r.contains("你患有抑郁症"));
        }
    }
}
