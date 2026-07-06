package com.nageoffer.ai.tinyagent.react.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;

import java.util.ArrayList;
import java.util.List;

public class Planner {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    public Planner(LlmClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
    }

    public Plan createPlan(String userMessage) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildPlannerPrompt());

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        ChatResponse response = llmClient.chatWithTools(
                messages, objectMapper.createArrayNode());
        String content = response.content();

        return parsePlan(userMessage, content);
    }

    public Plan replan(Plan currentPlan) {
        String goal = currentPlan.getGoal();
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildPlannerPrompt());

        String replanContext = """
                用户原始问题：%s

                之前的计划执行进度：
                %s
                请根据已完成步骤的结果和失败情况，重新规划剩余步骤。
                已完成的步骤不需要重复，只规划接下来要做的事。
                步骤编号从 %d 开始继续编号。
                """.formatted(
                goal,
                currentPlan.getProgressSummary(),
                currentPlan.getNextStepId());

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", replanContext);

        ChatResponse response = llmClient.chatWithTools(
                messages, objectMapper.createArrayNode());

        Plan newPlan = parsePlan(goal, response.content());

        List<PlanStep> merged = new ArrayList<>();
        for (PlanStep step : currentPlan.getSteps()) {
            if (step.getStatus() == PlanStepStatus.COMPLETED) {
                merged.add(step);
            }
        }
        merged.addAll(newPlan.getSteps());

        return new Plan(goal, merged);
    }

    private String buildPlannerPrompt() {
        StringBuilder toolList = new StringBuilder();
        for (Tool tool : toolRegistry.getTools()) {
            toolList.append("- ").append(tool.name())
                    .append("：").append(tool.description()).append("\n");
        }

        return """
                你是一个任务规划助手。根据用户的问题，将任务拆解为一系列可执行的步骤。

                可用工具：
                %s
                输出格式要求：
                用 JSON 数组表示计划，每个元素包含：
                - stepId：步骤编号（从 1 开始）
                - description：这一步要做什么（简洁明确）
                - toolHint：预计使用的工具名（如果是纯推理或综合步骤，填 null）

                规划原则：
                1. 每一步只做一件事，对应一次工具调用或一次推理判断
                2. 步骤之间按执行顺序排列——如果 B 依赖 A 的结果，A 必须排在 B 前面
                3. 最后一步必须是"综合以上结果，给出最终回复"，toolHint 填 null
                4. 步骤数量控制在 3-7 步，太少说明拆分不够，太多说明拆分过细
                5. 如果用户的需求包含条件判断（如"如果……就……"），把条件检查和条件执行拆成两步
                6. 只输出 JSON 数组，不要输出任何解释

                示例：
                用户问题：帮我查一下订单 88231 到了没，到了就退掉。
                输出：
                [
                  {"stepId":1,"description":"查询订单 88231 的详情，确认订单状态","toolHint":"queryOrder"},
                  {"stepId":2,"description":"如果订单已签收，为订单 88231 申请退款，退款原因为用户主动申请","toolHint":"applyRefund"},
                  {"stepId":3,"description":"综合以上结果，告知用户订单状态和退款进展","toolHint":null}
                ]
                """.formatted(toolList);
    }

    private Plan parsePlan(String goal, String content) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        try {
            String json = extractJsonArray(content);
            JsonNode arrayNode = objectMapper.readTree(json);

            List<PlanStep> steps = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                int stepId = node.get("stepId").asInt();
                String description = node.get("description").asText();
                String toolHint = node.has("toolHint") && !node.get("toolHint").isNull()
                        ? node.get("toolHint").asText() : null;
                steps.add(new PlanStep(stepId, description, toolHint));
            }
            return new Plan(goal, steps);
        } catch (Exception e) {
            System.out.println("[Planner] 计划解析失败，降级为单步执行：" + e.getMessage());
            List<PlanStep> fallback = List.of(
                    new PlanStep(1, goal, null),
                    new PlanStep(2, "综合以上结果，给出最终回复", null)
            );
            return new Plan(goal, fallback);
        }
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new IllegalArgumentException("未找到 JSON 数组");
    }
}
