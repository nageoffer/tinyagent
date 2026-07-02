package com.nageoffer.ai.tinyagent.react.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SummaryChatMemory implements ChatMemory {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final int threshold;
    private final int keepRecent;
    private final LlmClient llmClient;

    public SummaryChatMemory(LlmClient llmClient, int threshold, int keepRecent) {
        if (threshold < 4) {
            throw new IllegalArgumentException("阈值至少为 4（至少 2 轮对话才有压缩意义）");
        }
        if (keepRecent < 2) {
            throw new IllegalArgumentException("至少保留 2 条最近消息（1 轮对话）");
        }
        if (keepRecent >= threshold) {
            throw new IllegalArgumentException("保留条数必须小于阈值，否则永远不会触发压缩");
        }
        this.llmClient = llmClient;
        this.threshold = threshold;
        this.keepRecent = keepRecent;
    }

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
        if (messages.size() > threshold) {
            compress();
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

    private void compress() {
        int compressEnd = messages.size() - keepRecent;
        if (compressEnd <= 0) {
            return;
        }

        List<ChatMessage> toCompress = new ArrayList<>(messages.subList(0, compressEnd));
        List<ChatMessage> toKeep = new ArrayList<>(messages.subList(compressEnd, messages.size()));

        String summary = callLlmForSummary(toCompress);

        messages.clear();
        messages.add(ChatMessage.system("[对话摘要] " + summary));
        messages.addAll(toKeep);
    }

    private String callLlmForSummary(List<ChatMessage> toCompress) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode apiMessages = objectMapper.createArrayNode();

        ObjectNode systemMsg = apiMessages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content",
                "你是一个对话摘要助手。请将以下客服对话压缩为简洁的摘要，"
                        + "保留关键信息（订单号、商品名、金额、操作结果、用户诉求），"
                        + "去掉寒暄和重复内容。输出纯文本摘要，不要加任何格式标记。");

        StringBuilder conversation = new StringBuilder();
        for (ChatMessage msg : toCompress) {
            switch (msg.role()) {
                case USER -> conversation.append("用户：").append(msg.content()).append("\n");
                case ASSISTANT -> conversation.append("客服：").append(msg.content()).append("\n");
                case SYSTEM -> conversation.append("系统信息：").append(msg.content()).append("\n");
                default -> {
                }
            }
        }

        ObjectNode userMsg = apiMessages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "请压缩以下对话：\n\n" + conversation);

        ChatResponse response = llmClient.chatWithTools(apiMessages, objectMapper.createArrayNode());
        return response.content() != null ? response.content() : "";
    }
}
