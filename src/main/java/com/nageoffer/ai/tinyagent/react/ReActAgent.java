package com.nageoffer.ai.tinyagent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReActAgent {

    private static final int DEFAULT_MAX_STEPS = 10;
    private static final int DEFAULT_MAX_TOKENS = 8000;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final int maxSteps;
    private final int maxTokens;

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry) {
        this(llmClient, toolRegistry, DEFAULT_MAX_STEPS, DEFAULT_MAX_TOKENS);
    }

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                      int maxSteps, int maxTokens) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.objectMapper = llmClient.getObjectMapper();
        this.maxSteps = maxSteps;
        this.maxTokens = maxTokens;
    }

    public String run(String userMessage) {
        ArrayNode messages = objectMapper.createArrayNode();

        String systemPrompt = buildSystemPrompt();
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        ArrayNode tools = toolRegistry.buildToolsJsonArray(objectMapper);

        TokenBudget tokenBudget = new TokenBudget(maxTokens);
        tokenBudget.addMessage(systemPrompt);
        tokenBudget.addMessage(userMessage);

        RepeatDetector repeatDetector = new RepeatDetector();
        ProgressDetector progressDetector = new ProgressDetector(3);

        for (int step = 1; step <= maxSteps; step++) {
            System.out.println("\n===== 第 " + step + " 圈 =====");

            if (tokenBudget.isExceeded()) {
                System.out.println("[终止] Token 预算耗尽（约 "
                        + tokenBudget.getEstimatedTokens() + " Token）");
                return "抱歉，本次对话信息量较大，已达到处理上限。请尝试简化问题或分多次咨询。";
            }

            ChatResponse response = llmClient.chatWithTools(messages, tools);

            if (!response.hasToolCalls()) {
                String answer = response.content() != null ? response.content() : "";
                System.out.println("[最终答复] " + answer);
                return answer;
            }

            if (response.content() != null && !response.content().isBlank()) {
                System.out.println("[大脑] " + response.content().strip());
            }

            RepeatAction repeatAction = repeatDetector.check(response.toolCalls());

            if (repeatAction == RepeatAction.STOP) {
                System.out.println("[终止] 提醒后仍重复调用，强制停止");
                return "抱歉，我在处理您的问题时遇到了困难。请尝试换一种方式描述，或联系人工客服获取帮助。";
            }

            if (progressDetector.isStuck(response.content())) {
                System.out.println("[终止] 检测到无进展，连续多圈推理内容高度相似");
                return "抱歉，我在处理您的问题时遇到了困难，无法进一步推进。请尝试换一种方式描述，或联系人工客服获取帮助。";
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
            tokenBudget.addMessage(response.content());

            // 跳过执行仅适用于确定性只读工具（queryOrder、queryLogistics 等）
            // 副作用工具（如 applyRefund）不能跳过，需用幂等键去重
            if (repeatAction == RepeatAction.WARN) {
                System.out.println("[提醒] 检测到重复调用，注入提示");
                String hint = "你已经用相同的参数调用过这个工具，结果不会变化。"
                        + "请根据已有信息直接回复用户，不要重复调用。";
                for (ToolCallInfo tc : response.toolCalls()) {
                    ObjectNode toolMsg = messages.addObject();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", tc.id());
                    toolMsg.put("content", hint);
                    tokenBudget.addMessage(hint);
                }
                continue;
            }

            for (ToolCallInfo tc : response.toolCalls()) {
                System.out.println("[工具调用] " + tc.functionName()
                        + "(" + tc.arguments() + ")");

                String observation = toolRegistry.execute(
                        new Action(tc.functionName(), tc.arguments()));
                System.out.println("[工具结果] " + observation);

                ObjectNode toolMsg = messages.addObject();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", tc.id());
                toolMsg.put("content", observation);
                tokenBudget.addMessage(observation);
            }
        }

        System.out.println("[终止] 达到最大步数 " + maxSteps);
        return "抱歉，我思考了太多步仍未完成任务，请尝试换一种方式描述您的问题。";
    }

    private String buildSystemPrompt() {
        return """
                你是比特严选的智能客服助手，负责帮助用户解决商品咨询、\
                订单查询、物流追踪、退款换货等问题。
                请根据用户的问题，合理选择工具获取真实信息，\
                然后给出准确、友好的回复。
                
                注意事项：
                - 合理选择工具，每次调用后分析结果再决定下一步
                - 如果工具返回错误，分析原因并尝试换一种方式解决
                - 如果用户的问题超出工具能力范围，直接如实告知
                - 最终回复面向用户，不要暴露工具名、JSON 数据等内部细节
                - 避免重复调用相同的工具获取相同的信息
                """;
    }

    private enum RepeatAction {
        NORMAL, WARN, STOP
    }

    private static class RepeatDetector {

        private String lastCallSignature = "";
        private int repeatCount = 0;

        RepeatAction check(List<ToolCallInfo> toolCalls) {
            String currentSignature = buildSignature(toolCalls);
            if (currentSignature.equals(lastCallSignature)) {
                repeatCount++;
            } else {
                repeatCount = 0;
                lastCallSignature = currentSignature;
            }
            if (repeatCount >= 2) {
                return RepeatAction.STOP;
            }
            if (repeatCount == 1) {
                return RepeatAction.WARN;
            }
            return RepeatAction.NORMAL;
        }

        private String buildSignature(List<ToolCallInfo> toolCalls) {
            StringBuilder sb = new StringBuilder();
            for (ToolCallInfo tc : toolCalls) {
                sb.append(tc.functionName()).append(":")
                        .append(tc.arguments()).append(";");
            }
            return sb.toString();
        }
    }

    private static class TokenBudget {

        private static final double TOKENS_PER_CHAR = 1.0;

        private final int maxTokens;
        private int estimatedTokens = 0;

        TokenBudget(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        void addMessage(String content) {
            if (content != null) {
                estimatedTokens += estimateTokens(content);
            }
        }

        boolean isExceeded() {
            return estimatedTokens >= maxTokens;
        }

        int getEstimatedTokens() {
            return estimatedTokens;
        }

        private int estimateTokens(String text) {
            return (int) Math.ceil(text.length() * TOKENS_PER_CHAR);
        }
    }

    private static class ProgressDetector {

        private final int windowSize;
        private final List<String> recentContents = new ArrayList<>();

        ProgressDetector(int windowSize) {
            this.windowSize = windowSize;
        }

        boolean isStuck(String content) {
            if (content == null || content.isBlank()) {
                return false;
            }

            recentContents.add(content.strip());

            if (recentContents.size() < windowSize) {
                return false;
            }

            if (recentContents.size() > windowSize) {
                recentContents.remove(0);
            }

            for (int i = 1; i < recentContents.size(); i++) {
                if (computeSimilarity(recentContents.get(i - 1),
                        recentContents.get(i)) < 0.7) {
                    return false;
                }
            }
            return true;
        }

        private double computeSimilarity(String a, String b) {
            Set<Character> setA = toCharSet(a);
            Set<Character> setB = toCharSet(b);

            Set<Character> intersection = new HashSet<>(setA);
            intersection.retainAll(setB);

            Set<Character> union = new HashSet<>(setA);
            union.addAll(setB);

            if (union.isEmpty()) {
                return 0.0;
            }
            return (double) intersection.size() / union.size();
        }

        private Set<Character> toCharSet(String text) {
            Set<Character> set = new HashSet<>();
            for (char c : text.toCharArray()) {
                if (!Character.isWhitespace(c)) {
                    set.add(c);
                }
            }
            return set;
        }
    }
}
