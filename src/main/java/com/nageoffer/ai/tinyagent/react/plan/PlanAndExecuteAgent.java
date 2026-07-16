package com.nageoffer.ai.tinyagent.react.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.Action;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ToolCallInfo;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;

import java.util.List;

public class PlanAndExecuteAgent {

    private static final int MAX_STEP_ROUNDS = 3;
    private static final int MAX_STEP_RETRY = 1;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final Planner planner;
    private final int maxReplanCount;
    private final Reflector reflector;

    public PlanAndExecuteAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                               int maxReplanCount) {
        this(llmClient, toolRegistry, maxReplanCount, null);
    }

    public PlanAndExecuteAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                               int maxReplanCount, Reflector reflector) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.planner = new Planner(llmClient, toolRegistry);
        this.maxReplanCount = maxReplanCount;
        this.reflector = reflector;
    }

    public String run(String userMessage) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();

        System.out.println("[Plan] 正在规划...");
        Plan plan = planner.createPlan(userMessage);
        System.out.println("[Plan] 计划生成完成：");
        System.out.println(plan.getPlanOverview());

        ArrayNode tools = toolRegistry.buildToolsJsonArray(objectMapper);
        int replanCount = 0;

        while (!plan.isAllDone()) {
            PlanStep step = plan.nextPendingStep();
            if (step == null) break;

            step.setStatus(PlanStepStatus.EXECUTING);
            System.out.println("\n[Execute] ▶ Step " + step.getStepId()
                    + ": " + step.getDescription());

            String reflectionHint = null;
            boolean stepDone = false;
            int stepRetry = 0;

            while (!stepDone) {
                try {
                    String result = executeStep(step, plan, tools, reflectionHint);

                    if (reflector != null) {
                        ReflectionResult reflection = reflector.reflect(
                                step, result, plan.getGoal());

                        switch (reflection.getVerdict()) {
                            case PASS -> {
                                System.out.println("[Reflect] ✓ Step "
                                        + step.getStepId() + " 通过");
                                step.markCompleted(result);
                                stepDone = true;
                            }
                            case RETRY -> {
                                if (stepRetry < MAX_STEP_RETRY) {
                                    stepRetry++;
                                    reflectionHint = reflection.getSuggestion();
                                    System.out.println("[Reflect] ↻ Step "
                                            + step.getStepId() + " 第 "
                                            + stepRetry + " 次重试："
                                            + reflection.getAnalysis());
                                } else {
                                    System.out.println("[Reflect] Step "
                                            + step.getStepId()
                                            + " 重试次数已达上限，接受当前结果");
                                    step.markCompleted(result);
                                    stepDone = true;
                                }
                            }
                            case REPLAN -> {
                                System.out.println("[Reflect] ✗ Step "
                                        + step.getStepId() + " 需要重规划："
                                        + reflection.getAnalysis());
                                step.markFailed(reflection.getAnalysis());
                                stepDone = true;
                            }
                        }
                    } else {
                        step.markCompleted(result);
                        stepDone = true;
                    }
                } catch (Exception e) {
                    step.markFailed(e.getMessage());
                    System.out.println("[Execute] ✗ Step " + step.getStepId()
                            + " 异常：" + e.getMessage());
                    stepDone = true;
                }
            }

            if (step.getStatus() == PlanStepStatus.COMPLETED) {
                System.out.println("[Execute] ✓ Step " + step.getStepId() + " 完成");
            }

            if (step.getStatus() == PlanStepStatus.FAILED) {
                if (replanCount < maxReplanCount) {
                    replanCount++;
                    System.out.println("[Replan] 第 " + replanCount + " 次重新规划...");
                    plan = planner.replan(plan);
                    System.out.println("[Replan] 新计划：");
                    System.out.println(plan.getPlanOverview());
                } else {
                    System.out.println("[Replan] 已达最大重规划次数，跳过失败步骤");
                }
            }
        }

        System.out.println("\n[Plan] 执行完成：");
        System.out.println(plan.getProgressSummary());

        return extractFinalResult(plan);
    }

    private String executeStep(PlanStep step, Plan plan, ArrayNode tools,
                               String reflectionHint) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        String completedContext = buildCompletedContext(plan);
        boolean isSynthesisStep = step.getToolHint() == null;

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        if (isSynthesisStep) {
            String failedContext = buildFailedContext(plan);
            boolean hasCompleted = false;
            for (PlanStep s : plan.getSteps()) {
                if (s.getStatus() == PlanStepStatus.COMPLETED && s.getResult() != null) {
                    hasCompleted = true;
                    break;
                }
            }
            if (!hasCompleted && !failedContext.isEmpty()) {
                // 前置步骤全部失败、没有任何可用结果：别让模型对着空结果发懵返回空串
                // 直接据失败原因如实答复，省得综合步骤空转再被反思打回
                systemMsg.put("content", """
                        你是比特严选的智能客服助手。
                        之前的查询或操作均未成功，没有可用的执行结果。
                        请根据下面的失败原因，如实、得体地告知用户当前无法完成的部分，
                        并给出下一步建议（如稍后重试或联系人工客服）。
                        不要调用任何工具，直接输出回复文本。
                        %s
                        失败的步骤：
                        %s
                        """.formatted(
                        reflectionHint != null
                                ? "上一次尝试的反思：" + reflectionHint + "\n请据此调整回复内容。\n"
                                : "",
                        failedContext));
            } else {
                systemMsg.put("content", """
                        你是比特严选的智能客服助手。
                        请根据之前各步骤的执行结果，综合生成一段面向用户的完整回复。
                        不要调用任何工具，直接输出最终回复文本。
                        %s
                        之前步骤的执行结果：
                        %s
                        %s
                        """.formatted(
                        reflectionHint != null
                                ? "上一次尝试的反思：" + reflectionHint + "\n请据此调整回复内容。\n"
                                : "",
                        completedContext,
                        failedContext.isEmpty() ? ""
                                : "以下步骤执行失败，请在回复中如实告知用户：\n" + failedContext));
            }
        } else {
            String toolHintLine = step.getToolHint() != null
                    ? "建议使用的工具：" + step.getToolHint() + "（仅供参考，你可以根据实际情况选择其他工具）\n"
                    : "";
            String reflectLine = reflectionHint != null
                    ? "上一次尝试的反思：" + reflectionHint + "\n请根据这个反思调整执行方式。\n"
                    : "";
            systemMsg.put("content", """
                    你是比特严选的智能客服助手。你正在按计划执行一个任务。
                    当前步骤的目标：%s
                    %s%s
                    之前步骤的执行结果：
                    %s
                    重要约束：
                    - 只完成当前步骤描述的目标，不要做其他步骤的工作
                    - 每次最多调用一个工具
                    - 拿到工具结果后，如果已经满足当前步骤的目标，直接输出结果文本，不要继续调用工具
                    """.formatted(step.getDescription(), toolHintLine,
                    reflectLine, completedContext));
        }

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        // Executor 全程只看步骤描述和已完成步骤的结果，不直接读原始 goal
        // 用户的完整意图交给 Planner（拆步骤时）和 Reflector（评估时），这种信息不对称正是反思能发现推理偏差的前提
        userMsg.put("content", isSynthesisStep
                ? "请根据以上各步骤的执行结果，生成面向用户的最终回复。"
                : "请执行当前步骤：" + step.getDescription());

        ArrayNode stepTools = isSynthesisStep
                ? objectMapper.createArrayNode() : tools;

        for (int i = 0; i < MAX_STEP_ROUNDS; i++) {
            ChatResponse response = llmClient.chatWithTools(messages, stepTools);

            if (!response.hasToolCalls()) {
                return response.content() != null ? response.content() : "";
            }

            ObjectNode assistantMsg = messages.addObject();
            assistantMsg.put("role", "assistant");
            if (response.content() != null) {
                assistantMsg.put("content", response.content());
            } else {
                assistantMsg.putNull("content");
            }
            ArrayNode toolCallsNode = assistantMsg.putArray("tool_calls");

            for (ToolCallInfo tc : response.toolCalls()) {
                ObjectNode toolCallNode = toolCallsNode.addObject();
                toolCallNode.put("id", tc.id());
                toolCallNode.put("type", "function");
                ObjectNode funcNode = toolCallNode.putObject("function");
                funcNode.put("name", tc.functionName());
                funcNode.put("arguments", tc.arguments());

                System.out.println("[工具调用] " + tc.functionName()
                        + "(" + tc.arguments() + ")");

                String observation = toolRegistry.execute(
                        new Action(tc.functionName(), tc.arguments()));

                System.out.println("[工具结果] " + observation);

                ObjectNode toolMsg = messages.addObject();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", tc.id());
                toolMsg.put("content", observation);
            }
        }

        throw new RuntimeException("当前步骤未能在限定轮次内完成");
    }

    private String buildCompletedContext(Plan plan) {
        StringBuilder sb = new StringBuilder();
        for (PlanStep s : plan.getSteps()) {
            if (s.getStatus() == PlanStepStatus.COMPLETED && s.getResult() != null) {
                sb.append("Step ").append(s.getStepId()).append("（")
                        .append(s.getDescription()).append("）：")
                        .append(s.getResult()).append("\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : "（暂无已完成的步骤）";
    }

    private String buildFailedContext(Plan plan) {
        StringBuilder sb = new StringBuilder();
        for (PlanStep s : plan.getSteps()) {
            if (s.getStatus() == PlanStepStatus.FAILED && s.getResult() != null) {
                sb.append("Step ").append(s.getStepId()).append("（")
                        .append(s.getDescription()).append("）失败原因：")
                        .append(s.getResult()).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractFinalResult(Plan plan) {
        List<PlanStep> steps = plan.getSteps();
        for (int i = steps.size() - 1; i >= 0; i--) {
            PlanStep step = steps.get(i);
            if (step.getStatus() == PlanStepStatus.COMPLETED
                    && step.getResult() != null
                    && step.getToolHint() == null) {
                return step.getResult();
            }
        }
        StringBuilder sb = new StringBuilder();
        for (PlanStep step : steps) {
            if (step.getStatus() == PlanStepStatus.COMPLETED
                    && step.getResult() != null) {
                sb.append(step.getResult()).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
