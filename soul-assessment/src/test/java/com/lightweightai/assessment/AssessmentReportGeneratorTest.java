package com.lightweightai.assessment;

import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.ScaleType;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.LLMProvider.StreamEventHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AssessmentReportGenerator covering:
 * - Template fallback when LLMProvider is null
 * - Threshold logic per ScaleType (PHQ9, GAD7, PSS10)
 * - LLM-driven report generation
 * - Graceful degradation when LLM throws
 * - Template report structure
 */
@DisplayName("AssessmentReportGenerator")
class AssessmentReportGeneratorTest {

    // ==================== Helper ====================

    private static AssessmentResult result(ScaleType type, int score, String severity) {
        return new AssessmentResult("ar-test", "user-1", type, score, severity, null, 1000L);
    }

    // ==================== Null LLMProvider - template fallback ====================

    @Nested
    @DisplayName("Null LLMProvider - template fallback")
    class NullLlmProviderTests {

        private final AssessmentReportGenerator generator = new AssessmentReportGenerator(null);

        @Nested
        @DisplayName("PHQ9 thresholds (low <= 4, mid <= 9, high > 9)")
        class Phq9Thresholds {

            @Test
            @DisplayName("score 0 (boundary low) uses positive template")
            void scoreBoundaryLow() {
                String report = generator.generate(result(ScaleType.PHQ9, 0, "无抑郁"));

                assertTrue(report.contains("状态看起来相对良好"),
                        "low score should produce positive message");
            }

            @Test
            @DisplayName("score 4 (upper boundary of low) uses positive template")
            void scoreAtLowBoundary() {
                String report = generator.generate(result(ScaleType.PHQ9, 4, "无抑郁"));

                assertTrue(report.contains("状态看起来相对良好"),
                        "score exactly at threshold 4 should still be 'good'");
            }

            @Test
            @DisplayName("score 5 (just above low threshold) uses moderate template")
            void scoreJustAboveLow() {
                String report = generator.generate(result(ScaleType.PHQ9, 5, "轻度抑郁"));

                assertTrue(report.contains("正在经历一些困难"),
                        "score 5 should fall into moderate range");
            }

            @Test
            @DisplayName("score 9 (upper boundary of mid) uses moderate template")
            void scoreAtMidBoundary() {
                String report = generator.generate(result(ScaleType.PHQ9, 9, "轻度抑郁"));

                assertTrue(report.contains("正在经历一些困难"),
                        "score exactly at threshold 9 should still be moderate");
            }

            @Test
            @DisplayName("score 10 (just above mid threshold) uses high-severity template")
            void scoreJustAboveMid() {
                String report = generator.generate(result(ScaleType.PHQ9, 10, "中度抑郁"));

                assertTrue(report.contains("承受着比较大的压力"),
                        "score 10 should fall into high range");
            }

            @Test
            @DisplayName("score 27 (max) uses high-severity template")
            void scoreMax() {
                String report = generator.generate(result(ScaleType.PHQ9, 27, "重度抑郁"));

                assertTrue(report.contains("承受着比较大的压力"),
                        "maximum score should produce high-severity message");
            }
        }

        @Nested
        @DisplayName("GAD7 thresholds (same as PHQ9: low <= 4, mid <= 9, high > 9)")
        class Gad7Thresholds {

            @Test
            @DisplayName("score 3 (low) uses positive template")
            void lowScore() {
                String report = generator.generate(result(ScaleType.GAD7, 3, "无焦虑"));

                assertTrue(report.contains("状态看起来相对良好"));
            }

            @Test
            @DisplayName("score 4 (boundary) uses positive template")
            void boundaryLow() {
                String report = generator.generate(result(ScaleType.GAD7, 4, "无焦虑"));

                assertTrue(report.contains("状态看起来相对良好"));
            }

            @Test
            @DisplayName("score 7 (mid) uses moderate template")
            void midScore() {
                String report = generator.generate(result(ScaleType.GAD7, 7, "轻度焦虑"));

                assertTrue(report.contains("正在经历一些困难"));
            }

            @Test
            @DisplayName("score 9 (boundary mid) uses moderate template")
            void boundaryMid() {
                String report = generator.generate(result(ScaleType.GAD7, 9, "轻度焦虑"));

                assertTrue(report.contains("正在经历一些困难"));
            }

            @Test
            @DisplayName("score 15 (high) uses high-severity template")
            void highScore() {
                String report = generator.generate(result(ScaleType.GAD7, 15, "重度焦虑"));

                assertTrue(report.contains("承受着比较大的压力"));
            }
        }

        @Nested
        @DisplayName("PSS10 thresholds (different: low <= 13, mid <= 26, high > 26)")
        class Pss10Thresholds {

            @Test
            @DisplayName("score 10 (low) uses positive template")
            void lowScore() {
                String report = generator.generate(result(ScaleType.PSS10, 10, "低压力"));

                assertTrue(report.contains("状态看起来相对良好"));
            }

            @Test
            @DisplayName("score 13 (boundary low) uses positive template")
            void boundaryLow() {
                String report = generator.generate(result(ScaleType.PSS10, 13, "低压力"));

                assertTrue(report.contains("状态看起来相对良好"),
                        "score 13 should be at low boundary for PSS10");
            }

            @Test
            @DisplayName("score 14 (just above low) uses moderate template")
            void justAboveLow() {
                String report = generator.generate(result(ScaleType.PSS10, 14, "中等压力"));

                assertTrue(report.contains("正在经历一些困难"),
                        "score 14 should be moderate for PSS10");
            }

            @Test
            @DisplayName("score 20 (mid) uses moderate template")
            void midScore() {
                String report = generator.generate(result(ScaleType.PSS10, 20, "中等压力"));

                assertTrue(report.contains("正在经历一些困难"));
            }

            @Test
            @DisplayName("score 26 (boundary mid) uses moderate template")
            void boundaryMid() {
                String report = generator.generate(result(ScaleType.PSS10, 26, "中等压力"));

                assertTrue(report.contains("正在经历一些困难"),
                        "score 26 should be at mid boundary for PSS10");
            }

            @Test
            @DisplayName("score 27 (just above mid) uses high-severity template")
            void justAboveMid() {
                String report = generator.generate(result(ScaleType.PSS10, 27, "高压力"));

                assertTrue(report.contains("承受着比较大的压力"),
                        "score 27 should be high for PSS10");
            }

            @Test
            @DisplayName("score 40 (max) uses high-severity template")
            void maxScore() {
                String report = generator.generate(result(ScaleType.PSS10, 40, "高压力"));

                assertTrue(report.contains("承受着比较大的压力"));
            }
        }
    }

    // ==================== LLM available ====================

    @Nested
    @DisplayName("LLM available - uses LLM response")
    class LlmAvailableTests {

        @Test
        @DisplayName("generate() returns LLM-generated text")
        void returnsLlmText() {
            String expectedReport = "你的心理状态整体还不错，继续保持积极的生活态度。";

            LLMProvider mockProvider = capturingProvider(expectedReport, new AtomicReference<>());
            AssessmentReportGenerator generator = new AssessmentReportGenerator(mockProvider);
            AssessmentResult input = result(ScaleType.PHQ9, 3, "无抑郁");

            String report = generator.generate(input);

            assertEquals(expectedReport, report);
        }

        @Test
        @DisplayName("messages sent to LLM include system prompt and user prompt with score and severity")
        void messagesContainPromptWithScoreAndSeverity() {
            AtomicReference<List<ConversationMessage>> capturedMessages = new AtomicReference<>();

            LLMProvider capturingProvider = capturingProvider("mock report", capturedMessages);
            AssessmentReportGenerator generator = new AssessmentReportGenerator(capturingProvider);

            generator.generate(result(ScaleType.GAD7, 12, "中度焦虑"));

            List<ConversationMessage> messages = capturedMessages.get();
            assertNotNull(messages, "messages should be captured");
            assertEquals(2, messages.size(), "should have system + user messages");

            ConversationMessage systemMsg = messages.get(0);
            assertEquals(MessageRole.SYSTEM, systemMsg.getRole());
            assertTrue(systemMsg.getTextContent().contains("心理健康"),
                    "system prompt should reference mental health counseling");

            ConversationMessage userMsg = messages.get(1);
            assertEquals(MessageRole.USER, userMsg.getRole());
            String userText = userMsg.getTextContent();
            assertTrue(userText.contains("12"), "user prompt should contain the score");
            assertTrue(userText.contains("中度焦虑"), "user prompt should contain the severity");
            assertTrue(userText.contains("广泛性焦虑量表-7"),
                    "user prompt should contain the scale display name");
        }

        @Test
        @DisplayName("LLM is called with correct options (maxTokens and temperature)")
        void llmCalledWithCorrectOptions() {
            AtomicReference<LLMOptions> capturedOptions = new AtomicReference<>();

            LLMProvider provider = capturingProviderWithOptions("report",
                    new AtomicReference<>(), capturedOptions);

            AssessmentReportGenerator generator = new AssessmentReportGenerator(provider);
            generator.generate(result(ScaleType.PHQ9, 5, "轻度"));

            LLMOptions options = capturedOptions.get();
            assertNotNull(options, "LLMOptions should be captured");
            assertEquals(500, options.getMaxTokens());
            assertEquals(0.7, options.getTemperature(), 0.001);
        }
    }

    // ==================== LLM throws exception ====================

    @Nested
    @DisplayName("LLM throws exception - graceful degradation")
    class LlmExceptionTests {

        @Test
        @DisplayName("RuntimeException falls back to template report")
        void runtimeExceptionFallsBackToTemplate() {
            LLMProvider throwingProvider = throwingProvider(new RuntimeException("LLM service unavailable"));

            AssessmentReportGenerator generator = new AssessmentReportGenerator(throwingProvider);
            String report = generator.generate(result(ScaleType.PHQ9, 3, "无抑郁"));

            assertNotNull(report, "should not return null on LLM failure");
            assertTrue(report.contains("患者健康问卷-9"),
                    "fallback template should contain scale name");
            assertTrue(report.contains("状态看起来相对良好"),
                    "low PHQ9 score should produce positive template");
        }

        @Test
        @DisplayName("NullPointerException falls back to template report")
        void nullPointerExceptionFallsBackToTemplate() {
            LLMProvider throwingProvider = throwingProvider(new NullPointerException("unexpected null"));

            AssessmentReportGenerator generator = new AssessmentReportGenerator(throwingProvider);
            String report = generator.generate(result(ScaleType.GAD7, 16, "重度焦虑"));

            assertNotNull(report);
            assertTrue(report.contains("承受着比较大的压力"),
                    "high GAD7 score should use high-severity template on fallback");
        }

        @Test
        @DisplayName("exception during high PSS10 score falls back correctly")
        void exceptionHighPss10FallsBackCorrectly() {
            LLMProvider throwingProvider = throwingProvider(new RuntimeException("timeout"));

            AssessmentReportGenerator generator = new AssessmentReportGenerator(throwingProvider);
            String report = generator.generate(result(ScaleType.PSS10, 30, "高压力"));

            assertTrue(report.contains("压力知觉量表-10"),
                    "fallback should contain PSS10 display name");
            assertTrue(report.contains("承受着比较大的压力"),
                    "high PSS10 should use high-severity template on fallback");
        }
    }

    // ==================== Template report structure ====================

    @Nested
    @DisplayName("Template report structure")
    class TemplateStructureTests {

        private final AssessmentReportGenerator generator = new AssessmentReportGenerator(null);

        @Test
        @DisplayName("report contains scale display name")
        void containsScaleDisplayName() {
            String report = generator.generate(result(ScaleType.PHQ9, 5, "轻度抑郁"));
            assertTrue(report.contains("患者健康问卷-9"));

            report = generator.generate(result(ScaleType.GAD7, 5, "轻度焦虑"));
            assertTrue(report.contains("广泛性焦虑量表-7"));

            report = generator.generate(result(ScaleType.PSS10, 15, "中等压力"));
            assertTrue(report.contains("压力知觉量表-10"));
        }

        @Test
        @DisplayName("report contains numeric score")
        void containsNumericScore() {
            String report = generator.generate(result(ScaleType.PHQ9, 17, "中重度抑郁"));

            assertTrue(report.contains("17"),
                    "report should contain the raw score");
        }

        @Test
        @DisplayName("report contains severity label")
        void containsSeverityLabel() {
            String report = generator.generate(result(ScaleType.PHQ9, 17, "中重度抑郁"));

            assertTrue(report.contains("中重度抑郁"),
                    "report should contain the severity string");
        }

        @Test
        @DisplayName("report starts with acknowledgment")
        void startsWithAcknowledgment() {
            String report = generator.generate(result(ScaleType.GAD7, 2, "无焦虑"));

            assertTrue(report.startsWith("感谢你完成了"),
                    "template should start with a thank-you acknowledgment");
        }

        @Test
        @DisplayName("report contains score line with formatted score and severity")
        void containsScoreLine() {
            String report = generator.generate(result(ScaleType.PSS10, 20, "中等压力"));

            assertTrue(report.contains("你的得分是 20 分"),
                    "report should contain formatted score line");
            assertTrue(report.contains("结果显示「中等压力」"),
                    "report should contain severity in angle quotes");
        }
    }

    // ==================== Helpers ====================

    private static LLMProvider capturingProvider(
            String responseText,
            AtomicReference<List<ConversationMessage>> capturedMessages) {
        return capturingProviderWithOptions(responseText, capturedMessages, new AtomicReference<>());
    }

    private static LLMProvider capturingProviderWithOptions(
            String responseText,
            AtomicReference<List<ConversationMessage>> capturedMessages,
            AtomicReference<LLMOptions> capturedOptions) {
        return new StubLLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                capturedMessages.set(messages);
                capturedOptions.set(options);
                ConversationMessage reply = ConversationMessage.builder()
                        .role(MessageRole.ASSISTANT)
                        .textContent(responseText)
                        .build();
                return LLMResponse.builder().message(reply).build();
            }
        };
    }

    private static LLMProvider throwingProvider(RuntimeException exception) {
        return new StubLLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                throw exception;
            }
        };
    }

    private static abstract class StubLLMProvider implements LLMProvider {
        @Override
        public CompletableFuture<LLMResponse> completeAsync(
                List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options,
                StreamEventHandler handler) {
            throw new UnsupportedOperationException("not used in tests");
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "stub"; }
    }
}
