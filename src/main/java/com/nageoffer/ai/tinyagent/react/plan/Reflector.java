package com.nageoffer.ai.tinyagent.react.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.ChatResponse;
import com.nageoffer.ai.tinyagent.react.LlmClient;

public class Reflector {

    private final LlmClient llmClient;

    public Reflector(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ReflectionResult reflect(PlanStep step, String stepResult, String goal) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildReflectionPrompt());

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", """
                用户原始需求：%s
                当前步骤目标：%s
                执行结果：%s
                """.formatted(goal, step.getDescription(), stepResult));

        ChatResponse response = llmClient.chatWithTools(
                messages, objectMapper.createArrayNode());

        return parseReflection(response.content());
    }

    private String buildReflectionPrompt() {
        return """
                你是一个执行质量评审员。请评估当前步骤的执行结果是否达成了步骤目标。

                请严格按照以下 JSON 格式输出，不要输出任何其他内容：
                {"verdict":"PASS 或 RETRY 或 REPLAN","analysis":"一句话说明评估结论","suggestion":"改进建议"}

                评估标准：
                - PASS：执行结果已充分满足步骤目标，信息完整且准确
                - RETRY：执行结果存在明显偏差，可通过调整执行方式修正。典型偏差包括：遗漏了用户关注的重点、回答过于笼统、只做了全维度罗列却没有结合用户的使用场景给出针对性侧重、没有给出明确推荐而是"各有优势看你偏好"这类和稀泥的结论
                - REPLAN：当前步骤遇到了无法通过重试解决的问题（如工具返回错误、所需数据不存在），需要调整后续计划

                注意：
                - 如果执行结果包含错误信息（如"未找到""不存在""error"），应判定为 REPLAN
                - 对综合回复类步骤（目标包含"综合""最终回复"），只要覆盖了已完成步骤的关键信息即可判定 PASS
                - 只输出 JSON，不要输出任何解释文字
                """;
    }

    private ReflectionResult parseReflection(String content) {
        ObjectMapper objectMapper = llmClient.getObjectMapper();
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);

            String verdictStr = node.path("verdict").asText("PASS");
            ReflectionResult.Verdict verdict = switch (verdictStr) {
                case "RETRY" -> ReflectionResult.Verdict.RETRY;
                case "REPLAN" -> ReflectionResult.Verdict.REPLAN;
                default -> ReflectionResult.Verdict.PASS;
            };

            String analysis = node.path("analysis").asText("");
            String suggestion = node.path("suggestion").asText("");

            return new ReflectionResult(verdict, analysis, suggestion);
        } catch (Exception e) {
            System.out.println("[Reflect] 反思结果解析失败，默认 PASS：" + e.getMessage());
            return new ReflectionResult(
                    ReflectionResult.Verdict.PASS, "解析失败，默认通过", "");
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new IllegalArgumentException("未找到 JSON 对象");
    }
}
