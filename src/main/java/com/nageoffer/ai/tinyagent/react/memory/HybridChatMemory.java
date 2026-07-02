package com.nageoffer.ai.tinyagent.react.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HybridChatMemory implements ChatMemory {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final int recentSize;
    private final int summaryThreshold;
    private final LlmClient llmClient;

    public HybridChatMemory(LlmClient llmClient, int recentSize, int summaryThreshold) {
        if (recentSize < 2) {
            throw new IllegalArgumentException("近期窗口至少为 2");
        }
        if (summaryThreshold <= recentSize) {
            throw new IllegalArgumentException("摘要阈值必须大于近期窗口大小");
        }
        this.llmClient = llmClient;
        this.recentSize = recentSize;
        this.summaryThreshold = summaryThreshold;
    }

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
        if (messages.size() > summaryThreshold) {
            compressOlderMessages();
        }
    }

    @Override
    public List<ChatMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    private void compressOlderMessages() {
        int compressEnd = messages.size() - recentSize;
        if (compressEnd <= 0) {
            return;
        }

        List<ChatMessage> olderMessages = new ArrayList<>(messages.subList(0, compressEnd));
        List<ChatMessage> recentMessages = new ArrayList<>(messages.subList(compressEnd, messages.size()));

        String existingSummary = extractExistingSummary(olderMessages);
        String newSummary = callLlmForSummary(olderMessages, existingSummary);

        messages.clear();
        messages.add(ChatMessage.system("[对话摘要] " + newSummary));
        messages.addAll(recentMessages);
    }

    private String extractExistingSummary(List<ChatMessage> olderMessages) {
        for (ChatMessage msg : olderMessages) {
            if (msg.role() == ChatMessage.Role.SYSTEM
                    && msg.content() != null
                    && msg.content().startsWith("[对话摘要]")) {
                return msg.content().substring("[对话摘要] ".length());
            }
        }
        return null;
    }

    private String callLlmForSummary(List<ChatMessage> toCompress, String existingSummary) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode apiMessages = objectMapper.createArrayNode();

        ObjectNode systemMsg = apiMessages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content",
                "你是一个对话摘要助手。请将以下客服对话压缩为简洁的摘要，"
                        + "保留关键信息（订单号、商品名、金额、操作结果、用户诉求），"
                        + "去掉寒暄和重复内容。输出纯文本摘要，不要加任何格式标记。");

        StringBuilder conversation = new StringBuilder();
        if (existingSummary != null) {
            conversation.append("之前的对话摘要：\n").append(existingSummary).append("\n\n");
            conversation.append("后续新增的对话：\n");
        }
        for (ChatMessage msg : toCompress) {
            if (msg.role() == ChatMessage.Role.SYSTEM
                    && msg.content() != null
                    && msg.content().startsWith("[对话摘要]")) {
                continue;
            }
            switch (msg.role()) {
                case USER -> conversation.append("用户：").append(msg.content()).append("\n");
                case ASSISTANT -> conversation.append("客服：").append(msg.content()).append("\n");
                default -> {
                }
            }
        }

        ObjectNode userMsg = apiMessages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content",
                "请将以下内容整合成一段完整的对话摘要：\n\n" + conversation);

        ChatResponse response = llmClient.chatWithTools(apiMessages, objectMapper.createArrayNode());
        return response.content() != null ? response.content() : "";
    }
}
