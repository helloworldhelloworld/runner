package com.lightweightai.kernel.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Azure Speech Services Provider
 *
 * Features:
 * - High-quality Chinese neural voices
 * - SSML support for emotion control
 * - Multiple voice styles (calm, cheerful, gentle, etc.)
 */
public class AzureSpeechProvider implements SpeechProvider {

    private static final Logger logger = LoggerFactory.getLogger(AzureSpeechProvider.class);

    private final String apiKey;
    private final String region;
    private final String voiceName;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Emotion to Azure voice style mapping
    private static final Map<String, String> EMOTION_STYLE_MAP = new HashMap<>();
    static {
        EMOTION_STYLE_MAP.put("平静", "calm");
        EMOTION_STYLE_MAP.put("温柔", "gentle");
        EMOTION_STYLE_MAP.put("开心", "cheerful");
        EMOTION_STYLE_MAP.put("悲伤", "sad");
        EMOTION_STYLE_MAP.put("关心", "empathetic");
        EMOTION_STYLE_MAP.put("鼓励", "cheerful");
        EMOTION_STYLE_MAP.put("沮丧", "sad");
        // Default
        EMOTION_STYLE_MAP.put("default", "gentle");
    }

    /**
     * Create Azure Speech Provider
     *
     * @param apiKey Azure Speech API key
     * @param region Azure region (e.g., "eastasia", "southeastasia")
     * @param voiceName Voice name (e.g., "zh-CN-XiaoxiaoNeural" for female, "zh-CN-YunxiNeural" for male)
     */
    public AzureSpeechProvider(String apiKey, String region, String voiceName) {
        this.apiKey = apiKey;
        this.region = region;
        this.voiceName = voiceName != null ? voiceName : "zh-CN-XiaoxiaoNeural";
        this.httpClient = new OkHttpClient.Builder().build();
        this.objectMapper = new ObjectMapper();

        logger.info("Azure Speech Provider initialized with voice: {}", this.voiceName);
    }

    @Override
    public CompletableFuture<String> recognizeAsync(InputStream audioStream, String audioFormat) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Azure Speech-to-Text API
                String endpoint = String.format(
                    "https://%s.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=zh-CN",
                    region
                );

                byte[] audioBytes = audioStream.readAllBytes();

                Request request = new Request.Builder()
                    .url(endpoint)
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Content-Type", "audio/wav")
                    .post(RequestBody.create(audioBytes, MediaType.parse("audio/wav")))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new RuntimeException("Azure STT failed: " + response.code());
                    }

                    String responseBody = response.body().string();
                    JsonNode json = objectMapper.readTree(responseBody);

                    return json.get("DisplayText").asText();
                }

            } catch (IOException e) {
                logger.error("Speech recognition failed", e);
                throw new RuntimeException("Speech recognition failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> synthesizeAsync(String text, String emotion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Map emotion to Azure style
                String style = EMOTION_STYLE_MAP.getOrDefault(emotion, "gentle");

                // Build SSML with emotion
                String ssml = buildSSML(text, style);

                // Azure Text-to-Speech API
                String endpoint = String.format(
                    "https://%s.tts.speech.microsoft.com/cognitiveservices/v1",
                    region
                );

                Request request = new Request.Builder()
                    .url(endpoint)
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Content-Type", "application/ssml+xml")
                    .header("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                    .post(RequestBody.create(ssml, MediaType.parse("application/ssml+xml")))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String error = response.body() != null ? response.body().string() : "Unknown error";
                        logger.error("Azure TTS failed: {} - {}", response.code(), error);
                        throw new RuntimeException("Azure TTS failed: " + response.code());
                    }

                    return response.body().bytes();
                }

            } catch (IOException e) {
                logger.error("Speech synthesis failed", e);
                throw new RuntimeException("Speech synthesis failed", e);
            }
        });
    }

    /**
     * Build SSML with emotion style
     */
    private String buildSSML(String text, String style) {
        return String.format(
            "<speak version='1.0' xml:lang='zh-CN'>" +
            "<voice name='%s'>" +
            "<mstts:express-as style='%s'>" +
            "%s" +
            "</mstts:express-as>" +
            "</voice>" +
            "</speak>",
            voiceName,
            style,
            escapeXml(text)
        );
    }

    /**
     * Escape XML special characters
     */
    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    @Override
    public String getProviderName() {
        return "azure-speech";
    }
}
