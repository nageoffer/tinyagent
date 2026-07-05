package com.nageoffer.ai.tinyagent.react.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.Tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ToolFilter {

    private final LlmClient llmClient;

    public ToolFilter(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public List<Tool> filter(Collection<Tool> allTools, String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return new ArrayList<>(allTools);
        }

        StringBuilder toolList = new StringBuilder();
        List<Tool> toolOrder = new ArrayList<>(allTools);
        for (Tool tool : toolOrder) {
            toolList.append("- ").append(tool.name())
                    .append("：").append(tool.description()).append("\n");
        }

        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", """
                你是一个工具路由助手。根据用户的问题，从候选工具列表中选出需要用到的工具。
                只输出工具名（如 queryOrder），每行一个，不要输出任何解释。
                如果不确定，宁可多选不要漏选。
                如果用户的问题不需要任何工具（如打招呼、闲聊），输出 NONE。""");

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content",
                "用户问题：" + userQuery + "\n\n候选工具列表：\n" + toolList);

        try {
            ChatResponse response = llmClient.chatWithTools(
                    messages, objectMapper.createArrayNode());

            if (response.content() == null || response.content().isBlank()) {
                return new ArrayList<>(allTools);
            }

            String content = response.content().strip();
            if ("NONE".equalsIgnoreCase(content)) {
                return new ArrayList<>();
            }

            Set<String> selectedNames = new HashSet<>();
            for (String line : content.split("\n")) {
                String name = line.strip()
                        .replace("- ", "")
                        .replace("*", "");
                if (!name.isBlank()) {
                    selectedNames.add(name);
                }
            }

            List<Tool> result = new ArrayList<>();
            for (Tool tool : toolOrder) {
                if (selectedNames.contains(tool.name())) {
                    result.add(tool);
                }
            }

            return result;
        } catch (Exception e) {
            System.out.println("[ToolFilter] 筛选失败，回退到全量工具：" + e.getMessage());
            return new ArrayList<>(allTools);
        }
    }
}
