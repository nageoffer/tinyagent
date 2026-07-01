package com.nageoffer.ai.tinyagent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ReActAgent {

    private static final int MAX_STEPS = 10;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.objectMapper = llmClient.getObjectMapper();
    }

    public String run(String userMessage) {
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildSystemPrompt());

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        ArrayNode tools = toolRegistry.buildToolsJsonArray(objectMapper);

        for (int step = 1; step <= MAX_STEPS; step++) {
            System.out.println("\n===== 第 " + step + " 圈 =====");

            ChatResponse response = llmClient.chatWithTools(messages, tools);

            if (!response.hasToolCalls()) {
                String answer = response.content() != null ? response.content() : "";
                System.out.println("[最终答复] " + answer);
                return answer;
            }

            if (response.content() != null && !response.content().isBlank()) {
                System.out.println("[大脑] " + response.content().strip());
            }

            ObjectNode assistantMsg = messages.addObject();
            assistantMsg.put("role", "assistant");
            if (response.content() != null) {
                assistantMsg.put("content", response.content());
            } else {
                assistantMsg.putNull("content");
            }
            ArrayNode tcArray = assistantMsg.putArray("tool_calls");
            for (ToolCallInfo tc : response.toolCalls()) {
                ObjectNode tcNode = tcArray.addObject();
                tcNode.put("id", tc.id());
                tcNode.put("type", "function");
                ObjectNode funcNode = tcNode.putObject("function");
                funcNode.put("name", tc.functionName());
                funcNode.put("arguments", tc.arguments());
            }

            for (ToolCallInfo tc : response.toolCalls()) {
                System.out.println("[工具调用] " + tc.functionName() + "(" + tc.arguments() + ")");

                String observation = toolRegistry.execute(new Action(tc.functionName(), tc.arguments()));
                System.out.println("[工具结果] " + observation);

                ObjectNode toolMsg = messages.addObject();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", tc.id());
                toolMsg.put("content", observation);
            }
        }

        return "抱歉，我思考了太多步仍未完成任务，请尝试换一种方式描述您的问题。";
    }

    private String buildSystemPrompt() {
        return """
                你是比特严选的智能客服助手，负责帮助用户解决商品咨询、订单查询、物流追踪、退款换货等问题。
                请根据用户的问题，合理选择工具获取真实信息，然后给出准确、友好的回复。

                注意事项：
                - 合理选择工具，每次调用后分析结果再决定下一步
                - 如果工具返回错误，分析原因并尝试换一种方式解决
                - 如果用户的问题超出工具能力范围，直接如实告知
                - 最终回复面向用户，不要暴露工具名、JSON 数据等内部细节
                """;
    }
}
