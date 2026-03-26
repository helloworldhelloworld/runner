package com.lightweightai.kernel.speech;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AzureSpeechProvider - Azure 语音服务")
class AzureSpeechProviderTest {

    private AzureSpeechProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AzureSpeechProvider("test-key", "eastasia", null);
    }

    @Test
    @DisplayName("提供者名称为 azure-speech")
    void shouldReturnCorrectProviderName() {
        assertEquals("azure-speech", provider.getProviderName());
    }

    @Test
    @DisplayName("默认语音为 zh-CN-XiaoxiaoNeural")
    void shouldDefaultToXiaoxiaoNeural() throws Exception {
        // Verify by checking the voiceName field via reflection
        java.lang.reflect.Field field = AzureSpeechProvider.class.getDeclaredField("voiceName");
        field.setAccessible(true);
        assertEquals("zh-CN-XiaoxiaoNeural", field.get(provider));
    }

    @Test
    @DisplayName("自定义语音名称")
    void shouldUseCustomVoiceName() throws Exception {
        AzureSpeechProvider custom = new AzureSpeechProvider("key", "westus", "zh-CN-YunxiNeural");
        java.lang.reflect.Field field = AzureSpeechProvider.class.getDeclaredField("voiceName");
        field.setAccessible(true);
        assertEquals("zh-CN-YunxiNeural", field.get(custom));
    }

    @Nested
    @DisplayName("SSML 构建")
    class SSMLBuilding {

        @Test
        @DisplayName("生成正确的 SSML 结构")
        void shouldBuildCorrectSSML() throws Exception {
            Method buildSSML = AzureSpeechProvider.class.getDeclaredMethod("buildSSML", String.class, String.class);
            buildSSML.setAccessible(true);

            String ssml = (String) buildSSML.invoke(provider, "你好世界", "gentle");

            assertTrue(ssml.contains("<speak"));
            assertTrue(ssml.contains("zh-CN"));
            assertTrue(ssml.contains("zh-CN-XiaoxiaoNeural"));
            assertTrue(ssml.contains("style='gentle'"));
            assertTrue(ssml.contains("你好世界"));
            assertTrue(ssml.contains("</speak>"));
        }

        @Test
        @DisplayName("XML 特殊字符被转义")
        void shouldEscapeXmlSpecialCharacters() throws Exception {
            Method escapeXml = AzureSpeechProvider.class.getDeclaredMethod("escapeXml", String.class);
            escapeXml.setAccessible(true);

            String result = (String) escapeXml.invoke(provider, "a & b < c > d \"e\" 'f'");

            assertEquals("a &amp; b &lt; c &gt; d &quot;e&quot; &apos;f&apos;", result);
        }

        @Test
        @DisplayName("SSML 中文本已转义")
        void shouldEscapeTextInSSML() throws Exception {
            Method buildSSML = AzureSpeechProvider.class.getDeclaredMethod("buildSSML", String.class, String.class);
            buildSSML.setAccessible(true);

            String ssml = (String) buildSSML.invoke(provider, "A & B", "calm");

            assertTrue(ssml.contains("A &amp; B"));
            assertFalse(ssml.contains("A & B"));
        }
    }

    @Nested
    @DisplayName("情绪到风格映射")
    class EmotionMapping {

        @Test
        @DisplayName("已知情绪映射正确")
        void shouldMapKnownEmotions() throws Exception {
            // Access the static map via reflection
            java.lang.reflect.Field field = AzureSpeechProvider.class.getDeclaredField("EMOTION_STYLE_MAP");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> map = (java.util.Map<String, String>) field.get(null);

            assertEquals("calm", map.get("平静"));
            assertEquals("gentle", map.get("温柔"));
            assertEquals("cheerful", map.get("开心"));
            assertEquals("sad", map.get("悲伤"));
            assertEquals("empathetic", map.get("关心"));
            assertEquals("cheerful", map.get("鼓励"));
            assertEquals("sad", map.get("沮丧"));
            assertEquals("gentle", map.get("default"));
        }
    }
}
