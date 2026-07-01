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
                ## 角色
                你是比特严选的智能客服助手，负责帮助用户解决商品咨询、订单查询、物流追踪、退款换货等问题。
                请根据用户的问题，合理选择工具获取真实信息，然后给出准确、友好的回复。

                ## 可用工具
                %s

                ## 思考与行动格式
                请严格按照以下格式交替进行思考和行动：

                Thought: <分析当前局面，明确下一步要做什么以及为什么>
                Action: <工具名，必须是上方工具列表中的一个>
                Action Input: <严格按照工具的参数 Schema 输出 JSON。无参数的工具输出 {}>

                系统会执行工具并返回 Observation（执行结果），然后你继续下一轮思考。
                当你收集到足够信息可以回答用户时，使用以下格式结束：

                Thought: <总结已有信息，说明为什么现在可以回答>
                Final Answer: <给用户的最终回复，语气友好、内容完整>

                ## 示例
                用户：帮我查一下订单 88231 的物流到哪了。

                Thought: 用户想查订单 88231 的物流信息。查物流需要运单号，我先通过 queryOrder 获取运单号。
                Action: queryOrder
                Action Input: {"orderId":"88231"}

                Observation: {"orderId":"88231","product":"比特 S10 Pro 扫地机","price":1999,"orderTime":"2026-06-20","signTime":"2026-06-22","status":"已签收","trackingNo":"SF1234567890"}

                Thought: 拿到运单号 SF1234567890，接下来查物流轨迹。
                Action: queryLogistics
                Action Input: {"trackingNo":"SF1234567890"}

                Observation: {"trackingNo":"SF1234567890","carrier":"顺丰速运","status":"已签收","traces":[{"time":"2026-06-20 18:20:00","desc":"快件已揽收"},{"time":"2026-06-21 09:10:00","desc":"快件到达上海转运中心"},{"time":"2026-06-22 11:35:00","desc":"快件已由本人签收"}]}

                Thought: 已拿到完整物流信息，可以回答用户。
                Final Answer: 您好，您的订单 88231（比特 S10 Pro 扫地机）的物流信息如下：
                - 物流公司：顺丰速运
                - 运单号：SF1234567890
                - 当前状态：已签收

                物流轨迹：
                - 2026-06-20 18:20 快件已揽收
                - 2026-06-21 09:10 快件到达上海转运中心
                - 2026-06-22 11:35 快件已由本人签收

                您的包裹已于 6 月 22 日签收。如有其他问题随时联系我！

                ## 注意事项
                - 每次只调用一个工具，等拿到 Observation 后再决定下一步
                - 必须先写 Thought 再写 Action，不要跳过思考步骤
                - Action 必须是工具列表中存在的工具名，不要编造不存在的工具
                - Action Input 必须是合法 JSON，字段名和类型严格匹配工具的参数 Schema
                - 不要自己编造 Observation，必须等系统返回真实结果
                - 如果工具返回错误，在 Thought 中分析原因并尝试换一种方式解决
                - 如果用户的问题超出工具能力范围，直接用 Final Answer 如实告知
                - Final Answer 面向用户，不要暴露工具名、JSON、参数 Schema 等内部细节
                """.formatted(toolRegistry.buildToolList());
    }

    private Action parseAction(String llmOutput) {
        String toolName = "";
        String toolInput = "";

        String[] lines = llmOutput.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("Action Input:")) {
                StringBuilder inputBuilder = new StringBuilder(
                        trimmed.substring("Action Input:".length()).trim());
                while (i + 1 < lines.length && !isPromptSection(lines[i + 1].trim())) {
                    i++;
                    if (!inputBuilder.isEmpty()) {
                        inputBuilder.append("\n");
                    }
                    inputBuilder.append(lines[i].trim());
                }
                toolInput = inputBuilder.toString().trim();
            } else if (trimmed.startsWith("Action:")) {
                toolName = trimmed.substring("Action:".length()).trim();
            }
        }
        return new Action(toolName, toolInput);
    }

    private boolean isPromptSection(String line) {
        return line.startsWith("Thought:")
                || line.startsWith("Action:")
                || line.startsWith("Action Input:")
                || line.startsWith("Observation:")
                || line.startsWith("Final Answer:");
    }

    private String extractFinalAnswer(String llmOutput) {
        int index = llmOutput.indexOf("Final Answer:");
        if (index >= 0) {
            return llmOutput.substring(index + "Final Answer:".length()).trim();
        }
        return llmOutput;
    }
}
