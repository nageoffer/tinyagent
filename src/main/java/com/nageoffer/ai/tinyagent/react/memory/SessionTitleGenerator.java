package com.nageoffer.ai.tinyagent.react.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;

public class SessionTitleGenerator {

    private final LlmClient llmClient;

    public SessionTitleGenerator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String generate(String userMessage, String assistantReply) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content",
                "请根据以下用户问题和客服回复，生成一个简短的会话标题（10 字以内）。"
                        + "只输出标题文字，不要加任何标点或解释。");

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "用户：" + userMessage + "\n客服：" + assistantReply);

        ChatResponse response = llmClient.chatWithTools(messages, objectMapper.createArrayNode());
        String title = response.content();
        if (title != null) {
            title = title.strip();
            if (title.length() > 20) {
                title = title.substring(0, 20);
            }
        }
        return title;
    }
}
