package com.nageoffer.ai.tinyagent.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class EmbeddingClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public EmbeddingClient(String apiUrl, String apiKey, String model) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public double[] embed(String text) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("input", text);

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.get("application/json; charset=utf-8")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseText = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Embedding API 调用失败：" + response.code());
                }

                JsonNode json = objectMapper.readTree(responseText);
                JsonNode embeddingNode = json.at("/data/0/embedding");
                double[] vector = new double[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = embeddingNode.get(i).asDouble();
                }
                return vector;
            }
        } catch (IOException e) {
            throw new RuntimeException("Embedding 调用失败：" + e.getMessage(), e);
        }
    }
}
