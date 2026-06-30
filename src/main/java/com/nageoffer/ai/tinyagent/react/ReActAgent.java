package com.nageoffer.ai.tinyagent.react;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReActAgent {

    private static final int MAX_STEPS = 10;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
    }

    public String run(String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt()));
        messages.add(Map.of("role", "user", "content", userMessage));

        for (int step = 1; step <= MAX_STEPS; step++) {
            System.out.println("\n===== 第 " + step + " 圈 =====");

            String llmOutput = llmClient.chat(messages);

            int finalAnswerIndex = llmOutput.indexOf("Final Answer:");
            if (finalAnswerIndex >= 0) {
                String thought = llmOutput.substring(0, finalAnswerIndex).trim();
                if (!thought.isBlank()) {
                    System.out.println("[大脑] " + thought);
                }
                String answer = extractFinalAnswer(llmOutput);
                System.out.println("[最终答复] " + answer);
                return answer;
            }

            System.out.println("[大脑] " + llmOutput);

            messages.add(Map.of("role", "assistant", "content", llmOutput));

            Action action = parseAction(llmOutput);
            if (action.toolName().isBlank()) {
                String answer = llmOutput.trim();
                System.out.println("[最终答复-兜底] " + answer);
                return answer;
            }

            System.out.println("[工具调用] " + action.toolName() + "(" + action.toolInput() + ")");

            String observation = toolRegistry.execute(action);
            System.out.println("[工具结果] " + observation);

            messages.add(Map.of("role", "user", "content", "Observation: " + observation));
        }

        return "抱歉，我思考了太多步仍未完成任务，请尝试换一种方式描述您的问题。";
    }

    private String buildSystemPrompt() {
        return """
                你是比特严选的智能客服助手。你可以使用以下工具来帮助用户解决问题。

                工具列表：
                %s
                请严格按照以下格式思考和行动：

                Thought: <你的思考过程，分析当前局面，决定下一步做什么>
                Action: <要调用的工具名，必须是工具列表中的一个>
                Action Input: <传给工具的参数>

                工具执行后你会收到 Observation（工具返回的结果），然后继续下一轮思考。
                重复上述过程，直到你收集到足够的信息来回答用户。

                当你准备好给出最终答案时，使用以下格式：

                Thought: <总结已有信息，说明为什么可以回答了>
                Final Answer: <给用户的最终回复>

                注意：
                - 每次只调用一个工具
                - 必须先 Thought 再 Action，不要跳过思考步骤
                - Action 必须是工具列表中存在的工具名，不要编造工具
                """.formatted(toolRegistry.buildToolList());
    }

    private Action parseAction(String llmOutput) {
        String toolName = "";
        String toolInput = "";

        for (String line : llmOutput.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Action Input:")) {
                toolInput = trimmed.substring("Action Input:".length()).trim();
            } else if (trimmed.startsWith("Action:")) {
                toolName = trimmed.substring("Action:".length()).trim();
            }
        }
        return new Action(toolName, toolInput);
    }

    private String extractFinalAnswer(String llmOutput) {
        int index = llmOutput.indexOf("Final Answer:");
        if (index >= 0) {
            return llmOutput.substring(index + "Final Answer:".length()).trim();
        }
        return llmOutput;
    }
}
