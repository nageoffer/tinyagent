package com.nageoffer.ai.tinyagent.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LlmClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public LlmClient(String apiUrl, String apiKey, String model) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    public String chat(List<Map<String, String>> messages) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);

            ArrayNode messagesArray = requestBody.putArray("messages");
            for (Map<String, String> message : messages) {
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", message.get("role"));
                messageNode.put("content", message.get("content"));
            }

            ArrayNode stopArray = requestBody.putArray("stop");
            stopArray.add("Observation:");

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                String responseText = body == null ? "" : body.string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("API 调用失败，状态码：" + response.code() + "，响应：" + responseText);
                }

                JsonNode responseJson = objectMapper.readTree(responseText);
                JsonNode contentNode = responseJson.at("/choices/0/message/content");
                if (contentNode.isMissingNode() || contentNode.isNull()) {
                    throw new RuntimeException("API 响应中缺少 choices[0].message.content：" + responseText);
                }
                return contentNode.asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("调用大模型失败：" + e.getMessage(), e);
        }
    }
}
