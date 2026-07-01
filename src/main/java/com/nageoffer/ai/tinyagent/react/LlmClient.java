package com.nageoffer.ai.tinyagent.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LlmClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    @Getter
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

    public ChatResponse chatWithTools(ArrayNode messages, ArrayNode tools) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);
            requestBody.set("messages", messages);
            requestBody.set("tools", tools);

            String responseText = doRequestRaw(requestBody);
            JsonNode responseJson = objectMapper.readTree(responseText);
            JsonNode choice = responseJson.at("/choices/0");
            String finishReason = choice.path("finish_reason").asText("");

            JsonNode message = choice.path("message");
            String content = message.path("content").isNull()
                    ? null : message.path("content").asText(null);

            List<ToolCallInfo> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode tc : toolCallsNode) {
                    String id = tc.path("id").asText();
                    String funcName = tc.at("/function/name").asText();
                    String arguments = tc.at("/function/arguments").asText();
                    toolCalls.add(new ToolCallInfo(id, funcName, arguments));
                }
            }

            return new ChatResponse(content, toolCalls, finishReason);
        } catch (IOException e) {
            throw new RuntimeException("调用大模型失败：" + e.getMessage(), e);
        }
    }

    private String doRequestRaw(ObjectNode requestBody) throws IOException {
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseText = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "API 调用失败，状态码：" + response.code() + "，响应：" + responseText);
            }
            return responseText;
        }
    }
}
