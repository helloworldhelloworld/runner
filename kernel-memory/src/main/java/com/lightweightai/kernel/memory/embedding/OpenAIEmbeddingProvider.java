package com.lightweightai.kernel.memory.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI Embedding API provider.
 * Uses text-embedding-3-small model (1536 dimensions) by default.
 */
public class OpenAIEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIEmbeddingProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/embeddings";
    private static final MediaType JSON = MediaType.parse("application/json");

    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public OpenAIEmbeddingProvider(String apiKey) {
        this(apiKey, "text-embedding-3-small", 1536);
    }

    public OpenAIEmbeddingProvider(String apiKey, String model, int dimensions) {
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public float[] embed(String text) {
        try {
            return embedAsync(text).get();
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed", e);
        }
    }

    @Override
    public CompletableFuture<float[]> embedAsync(String text) {
        return embedBatchAsync(Collections.singletonList(text))
            .thenApply(results -> results.isEmpty() ? new float[0] : results.get(0));
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            return embedBatchAsync(texts).get();
        } catch (Exception e) {
            throw new RuntimeException("Batch embedding failed", e);
        }
    }

    @Override
    public CompletableFuture<List<float[]>> embedBatchAsync(List<String> texts) {
        CompletableFuture<List<float[]>> future = new CompletableFuture<>();

        if (texts.isEmpty()) {
            future.complete(Collections.<float[]>emptyList());
            return future;
        }

        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", model);

            ArrayNode inputArray = requestBody.putArray("input");
            for (String text : texts) {
                inputArray.add(text);
            }

            Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsString(requestBody), JSON))
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("Embedding request failed", e);
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody body = response.body()) {
                        if (!response.isSuccessful()) {
                            String error = body != null ? body.string() : "Unknown error";
                            log.error("Embedding API error: {} - {}", response.code(), error);
                            future.completeExceptionally(new RuntimeException("API error: " + response.code()));
                            return;
                        }

                        String responseBody = body.string();
                        JsonNode json = mapper.readTree(responseBody);
                        JsonNode dataArray = json.get("data");

                        List<float[]> embeddings = new ArrayList<>();
                        for (JsonNode item : dataArray) {
                            JsonNode embeddingArray = item.get("embedding");
                            float[] embedding = new float[embeddingArray.size()];
                            for (int i = 0; i < embeddingArray.size(); i++) {
                                embedding[i] = (float) embeddingArray.get(i).asDouble();
                            }
                            embeddings.add(embedding);
                        }

                        log.debug("Generated {} embeddings", embeddings.size());
                        future.complete(embeddings);
                    } catch (Exception e) {
                        log.error("Failed to parse embedding response", e);
                        future.completeExceptionally(e);
                    }
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String getModelName() {
        return model;
    }
}
