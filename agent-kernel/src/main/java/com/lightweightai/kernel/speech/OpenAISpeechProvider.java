package com.lightweightai.kernel.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI Speech Provider (Whisper + TTS)
 *
 * Features:
 * - Whisper: State-of-the-art speech recognition
 * - TTS: High-quality text-to-speech with multiple voices
 */
public class OpenAISpeechProvider implements SpeechProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenAISpeechProvider.class);
    private static final String API_BASE_URL = "https://api.openai.com/v1";

    private final String apiKey;
    private final String ttsVoice;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Create OpenAI Speech Provider
     *
     * @param apiKey OpenAI API key
     * @param ttsVoice TTS voice (alloy, echo, fable, onyx, nova, shimmer)
     */
    public OpenAISpeechProvider(String apiKey, String ttsVoice) {
        this.apiKey = apiKey;
        this.ttsVoice = ttsVoice != null ? ttsVoice : "nova"; // nova is warm and friendly
        this.httpClient = new OkHttpClient.Builder().build();
        this.objectMapper = new ObjectMapper();

        logger.info("OpenAI Speech Provider initialized with voice: {}", this.ttsVoice);
    }

    @Override
    public CompletableFuture<String> recognizeAsync(InputStream audioStream, String audioFormat) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // OpenAI Whisper API
                byte[] audioBytes = audioStream.readAllBytes();

                RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "audio." + audioFormat,
                        RequestBody.create(audioBytes, MediaType.parse("audio/" + audioFormat)))
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart("language", "zh")
                    .build();

                Request request = new Request.Builder()
                    .url(API_BASE_URL + "/audio/transcriptions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(requestBody)
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String error = response.body() != null ? response.body().string() : "Unknown error";
                        logger.error("Whisper STT failed: {} - {}", response.code(), error);
                        throw new RuntimeException("Whisper STT failed: " + response.code());
                    }

                    String responseBody = response.body().string();
                    JsonNode json = objectMapper.readTree(responseBody);

                    return json.get("text").asText();
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
                // OpenAI TTS API
                // Note: OpenAI TTS doesn't support emotion control directly,
                // but different voices have different characteristics

                Map<String, Object> ttsRequest = new java.util.HashMap<>();
                ttsRequest.put("model", "tts-1");
                ttsRequest.put("input", text);
                ttsRequest.put("voice", selectVoiceByEmotion(emotion));
                ttsRequest.put("response_format", "mp3");
                String requestBody = objectMapper.writeValueAsString(ttsRequest);

                Request request = new Request.Builder()
                    .url(API_BASE_URL + "/audio/speech")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String error = response.body() != null ? response.body().string() : "Unknown error";
                        logger.error("OpenAI TTS failed: {} - {}", response.code(), error);
                        throw new RuntimeException("OpenAI TTS failed: " + response.code());
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
     * Select voice based on emotion
     */
    private String selectVoiceByEmotion(String emotion) {
        if (emotion == null) return ttsVoice;

        switch (emotion) {
            case "开心":
            case "鼓励":
                return "shimmer"; // Warm and enthusiastic
            case "平静":
            case "温柔":
                return "nova"; // Gentle and soothing
            case "悲伤":
            case "沮丧":
                return "onyx"; // Deep and empathetic
            case "关心":
                return "alloy"; // Balanced and caring
            default:
                return ttsVoice;
        }
    }

    @Override
    public String getProviderName() {
        return "openai-speech";
    }
}
