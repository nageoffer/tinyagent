package com.nageoffer.ai.tinyagent.react.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;

import java.util.List;

public class UserProfileExtractor {

    private final LlmClient llmClient;

    public UserProfileExtractor(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ExtractedProfile extract(List<ChatMessage> conversationHistory) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", """
                你是一个用户画像提取助手。根据以下客服对话记录，提取用户画像和交互记录。

                输出格式（严格遵守，参照示例）：
                [画像] 偏好和特征的描述
                [记录] 包含具体数据（订单号、编号、型号、金额等）的完整交互叙述，不要用模糊指代

                示例（仅供参考格式，实际内容根据对话提取）：
                [画像] 偏好智能家居品类（扫地机），预算 2000-3000 元，对产品质量敏感
                [记录] 用户因订单 88231 的比特 S10 Pro 扫地机（1999 元）出现无法回充的质量问题申请退款，退款编号 RF20260629001，预计 1-3 个工作日到账，随后咨询新款扫地机，对比了 S20 Pro 和 S20 Max，倾向 S20 Pro 但认为价格偏高

                规则：
                - 交互记录是一段完整叙述，所有关键数据（订单号、退款编号、金额、型号等）嵌入叙述中
                - 不要把数据拆成单独的 key=value 行，全部写进记录的叙述里
                - 不要重复对话原文，不要输出不确定的推测""");

        StringBuilder conversation = new StringBuilder();
        for (ChatMessage msg : conversationHistory) {
            switch (msg.role()) {
                case USER -> conversation.append("用户：").append(msg.content()).append("\n");
                case ASSISTANT -> conversation.append("客服：").append(msg.content()).append("\n");
                default -> { }
            }
        }

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "请从以下对话中提取信息：\n\n" + conversation);

        ChatResponse response = llmClient.chatWithTools(messages, objectMapper.createArrayNode());
        return parseExtractedProfile(response.content());
    }

    private ExtractedProfile parseExtractedProfile(String raw) {
        String profile = null;
        String record = null;

        if (raw == null) {
            return new ExtractedProfile(profile, record);
        }

        for (String line : raw.split("\n")) {
            line = line.strip();
            if (line.startsWith("[画像]")) {
                profile = line.substring("[画像]".length()).strip();
            } else if (line.startsWith("[记录]")) {
                record = line.substring("[记录]".length()).strip();
            }
        }
        return new ExtractedProfile(profile, record);
    }
}
