package com.lightweightai.kernel.speech;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Speech Provider interface for STT and TTS
 */
public interface SpeechProvider {

    /**
     * Speech-to-Text: Convert audio to text
     *
     * @param audioStream Audio input stream
     * @param audioFormat Audio format (e.g., "wav", "mp3", "webm")
     * @return Recognized text
     */
    CompletableFuture<String> recognizeAsync(InputStream audioStream, String audioFormat);

    /**
     * Text-to-Speech: Convert text to audio with emotion
     *
     * @param text Text to synthesize
     * @param emotion Emotion style (e.g., "calm", "cheerful", "empathetic", "gentle")
     * @return Audio bytes
     */
    CompletableFuture<byte[]> synthesizeAsync(String text, String emotion);

    /**
     * Get provider name
     */
    String getProviderName();
}
