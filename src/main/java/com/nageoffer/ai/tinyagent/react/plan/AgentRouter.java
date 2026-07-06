package com.nageoffer.ai.tinyagent.react.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;

public class AgentRouter {

    private final LlmClient llmClient;
    private final ReActAgent reactAgent;
    private final PlanAndExecuteAgent planAgent;

    public AgentRouter(LlmClient llmClient, ReActAgent reactAgent,
                       PlanAndExecuteAgent planAgent) {
        this.llmClient = llmClient;
        this.reactAgent = reactAgent;
        this.planAgent = planAgent;
    }

    public String route(String userMessage) {
        String mode = classifyComplexity(userMessage);
        System.out.println("[Router] 任务模式：" + mode);

        if ("plan".equals(mode)) {
            return planAgent.run(userMessage);
        } else {
            return reactAgent.run(userMessage);
        }
    }

    private String classifyComplexity(String userMessage) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", """
                判断用户的问题是简单任务还是复杂任务。
                简单任务：只需要一个工具调用或直接回答，输出 react
                复杂任务：需要多个工具协作、包含条件判断、或有多个子需求，输出 plan
                只输出 react 或 plan，不要输出其他内容。""");

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        ChatResponse response = llmClient.chatWithTools(
                messages, objectMapper.createArrayNode());
        String result = response.content().strip().toLowerCase();
        return result.contains("plan") ? "plan" : "react";
    }
}
