package com.nageoffer.ai.tinyagent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.context.ContextBudget;
import com.nageoffer.ai.tinyagent.react.context.ObservationFolder;
import com.nageoffer.ai.tinyagent.react.context.ToolFilter;
import com.nageoffer.ai.tinyagent.react.memory.ChatMemory;
import com.nageoffer.ai.tinyagent.react.memory.ChatMessage;
import com.nageoffer.ai.tinyagent.react.memory.LongTermMemoryRetriever;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReActAgent {

    private static final int DEFAULT_MAX_STEPS = 10;
    private static final int DEFAULT_MAX_TOKENS = 8000;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final int maxSteps;
    private final int maxTokens;
    private final ChatMemory chatMemory;
    private final LongTermMemoryRetriever memoryRetriever;
    private final ToolFilter toolFilter;
    private final ObservationFolder observationFolder;

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry) {
        this(llmClient, toolRegistry, DEFAULT_MAX_STEPS, DEFAULT_MAX_TOKENS,
                null, null, null, null);
    }

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                      int maxSteps, int maxTokens) {
        this(llmClient, toolRegistry, maxSteps, maxTokens,
                null, null, null, null);
    }

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                      int maxSteps, int maxTokens, ChatMemory chatMemory) {
        this(llmClient, toolRegistry, maxSteps, maxTokens,
                chatMemory, null, null, null);
    }

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                      int maxSteps, int maxTokens, ChatMemory chatMemory,
                      LongTermMemoryRetriever memoryRetriever) {
        this(llmClient, toolRegistry, maxSteps, maxTokens,
                chatMemory, memoryRetriever, null, null);
    }

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                      int maxSteps, int maxTokens, ChatMemory chatMemory,
                      LongTermMemoryRetriever memoryRetriever,
                      ToolFilter toolFilter, ObservationFolder observationFolder) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.objectMapper = llmClient.getObjectMapper();
        this.maxSteps = maxSteps;
        this.maxTokens = maxTokens;
        this.chatMemory = chatMemory;
        this.memoryRetriever = memoryRetriever;
        this.toolFilter = toolFilter;
        this.observationFolder = observationFolder;
    }

    public String run(String userMessage) {
        return run(userMessage, null);
    }

    public String run(String userMessage, String userId) {
        ArrayNode messages = objectMapper.createArrayNode();

        String systemPrompt = buildSystemPrompt();
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);

        ContextBudget budget = new ContextBudget(maxTokens);
        budget.addSystemPrompt(systemPrompt);

        if (memoryRetriever != null && userId != null) {
            String memoryContext = memoryRetriever.buildMemoryContext(userId, userMessage);
            if (!memoryContext.isBlank()) {
                String fullMemory = "以下是关于当前用户的历史信息，供你参考：\n" + memoryContext;
                if (budget.tryAddMemory(fullMemory)) {
                    ObjectNode memoryMsg = messages.addObject();
                    memoryMsg.put("role", "system");
                    memoryMsg.put("content", fullMemory);
                } else {
                    System.out.println("[上下文] 长期记忆超出预算，已跳过");
                }
            }
        }

        if (chatMemory != null) {
            chatMemory.add(ChatMessage.user(userMessage));
            for (ChatMessage mem : chatMemory.messages()) {
                ObjectNode memMsg = messages.addObject();
                switch (mem.role()) {
                    case SYSTEM -> {
                        memMsg.put("role", "system");
                        memMsg.put("content", mem.content());
                    }
                    case USER -> {
                        memMsg.put("role", "user");
                        memMsg.put("content", mem.content());
                    }
                    case ASSISTANT -> {
                        memMsg.put("role", "assistant");
                        memMsg.put("content", mem.content());
                    }
                    case TOOL -> {
                        memMsg.put("role", "tool");
                        memMsg.put("tool_call_id", mem.toolCallId());
                        memMsg.put("content", mem.content());
                    }
                }
                budget.addHistory(mem.content());
            }
            int historyBudget = budget.getHistoryBudget();
            if (budget.getHistoryTokens() > historyBudget) {
                System.out.println("[上下文] 对话历史（" + budget.getHistoryTokens()
                        + "）超出动态预算（" + historyBudget + "）");
            }
        } else {
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            budget.addHistory(userMessage);
        }

        Collection<Tool> candidateTools;
        if (toolFilter != null) {
            candidateTools = toolFilter.filter(
                    toolRegistry.getTools(), userMessage);
            System.out.println("[上下文] 工具筛选：全部 "
                    + toolRegistry.getTools().size()
                    + " 个 → 语义匹配 " + candidateTools.size() + " 个");
        } else {
            candidateTools = toolRegistry.getTools();
        }

        List<Tool> budgetedTools = new ArrayList<>();
        for (Tool tool : candidateTools) {
            String toolDesc = tool.name() + "：" + tool.description()
                    + (tool.parameters() != null ? tool.parameters() : "");
            if (budget.tryAddToolDescription(toolDesc)) {
                budgetedTools.add(tool);
            }
        }
        if (budgetedTools.size() < candidateTools.size()) {
            System.out.println("[上下文] 工具描述超出预算裁剪："
                    + candidateTools.size() + " → " + budgetedTools.size() + " 个");
        }
        ArrayNode tools = toolRegistry.buildToolsJsonArray(objectMapper, budgetedTools);

        Map<String, Tool> dynamicToolMap = new LinkedHashMap<>();

        RepeatDetector repeatDetector = new RepeatDetector();
        ProgressDetector progressDetector = new ProgressDetector(3);

        for (int step = 1; step <= maxSteps; step++) {
            System.out.println("\n===== 第 " + step + " 圈 =====");

            if (budget.isExceeded()) {
                System.out.println("[终止] Token 预算耗尽（" + budget.getReport() + "）");
                return "抱歉，本次对话信息量较大，已达到处理上限。请尝试简化问题或分多次咨询。";
            }

            ChatResponse response = llmClient.chatWithTools(messages, tools);

            if (!response.hasToolCalls()) {
                String answer = response.content() != null ? response.content() : "";
                System.out.println("[最终答复] " + answer);
                if (chatMemory != null) {
                    chatMemory.add(ChatMessage.assistant(answer));
                }
                System.out.println("[预算] " + budget.getReport());
                return answer;
            }

            if (response.content() != null && !response.content().isBlank()) {
                System.out.println("[大脑] " + response.content().strip());
            }

            RepeatAction repeatAction = repeatDetector.check(response.toolCalls());

            if (repeatAction == RepeatAction.STOP) {
                System.out.println("[终止] 提醒后仍重复调用，强制停止");
                String stopMsg = "抱歉，我在处理您的问题时遇到了困难。请尝试换一种方式描述，或联系人工客服获取帮助。";
                if (chatMemory != null) {
                    chatMemory.add(ChatMessage.assistant(stopMsg));
                }
                return stopMsg;
            }

            if (progressDetector.isStuck(response.content())) {
                System.out.println("[终止] 检测到无进展，连续多圈推理内容高度相似");
                String stuckMsg = "抱歉，我在处理您的问题时遇到了困难，无法进一步推进。请尝试换一种方式描述，或联系人工客服获取帮助。";
                if (chatMemory != null) {
                    chatMemory.add(ChatMessage.assistant(stuckMsg));
                }
                return stuckMsg;
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
            budget.addCurrentTurn(response.content());

            if (repeatAction == RepeatAction.WARN) {
                System.out.println("[提醒] 检测到重复调用，注入提示");
                String hint = "你已经用相同的参数调用过这个工具，结果不会变化。"
                        + "请根据已有信息直接回复用户，不要重复调用。";
                for (ToolCallInfo tc : response.toolCalls()) {
                    ObjectNode toolMsg = messages.addObject();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", tc.id());
                    toolMsg.put("content", hint);
                    budget.addCurrentTurn(hint);
                }
                continue;
            }

            for (ToolCallInfo tc : response.toolCalls()) {
                System.out.println("[工具调用] " + tc.functionName()
                        + "(" + tc.arguments() + ")");

                Tool targetTool = toolRegistry.getTool(tc.functionName());
                if (targetTool == null) {
                    targetTool = dynamicToolMap.get(tc.functionName());
                }
                String observation = targetTool != null
                        ? targetTool.invoke(tc.arguments() == null ? "" : tc.arguments())
                        : "{\"error\":\"未找到工具：" + tc.functionName() + "\"}";

                if (observationFolder != null) {
                    String folded = observationFolder.fold(observation);
                    if (folded.length() < observation.length()) {
                        System.out.println("[上下文] Observation 折叠："
                                + observation.length() + " → " + folded.length() + " 字符");
                    }
                    observation = folded;
                }

                System.out.println("[工具结果] " + observation);

                ObjectNode toolMsg = messages.addObject();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", tc.id());
                toolMsg.put("content", observation);
                budget.addCurrentTurn(observation);

                if (targetTool instanceof DynamicToolProvider provider) {
                    List<Tool> newTools = provider.dynamicTools();
                    if (!newTools.isEmpty()) {
                        Set<String> existingNames = new HashSet<>();
                        for (Tool t : budgetedTools) {
                            existingNames.add(t.name());
                        }
                        for (Tool newTool : newTools) {
                            dynamicToolMap.put(newTool.name(), newTool);
                            if (existingNames.add(newTool.name())) {
                                budgetedTools.add(newTool);
                            }
                        }
                        tools = toolRegistry.buildToolsJsonArray(objectMapper, budgetedTools);
                        System.out.println("[动态工具] 解锁 " + newTools.size()
                                + " 个技能专属工具："
                                + newTools.stream().map(Tool::name).toList());
                    }
                }
            }
        }

        System.out.println("[终止] 达到最大步数 " + maxSteps);
        String maxStepMsg = "抱歉，我思考了太多步仍未完成任务，请尝试换一种方式描述您的问题。";
        if (chatMemory != null) {
            chatMemory.add(ChatMessage.assistant(maxStepMsg));
        }
        return maxStepMsg;
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
                - 注意对话上下文，用户可能会用代词（如"它""那个""这个订单"）引用之前提到的内容

                工具选择原则：
                - 查订单状态、物流轨迹这类实时数据，直接调对应的查询工具，不要先去知识库检索
                - 对比商品的具体规格参数，用商品对比工具拿结构化数据，不要用知识库检索代替
                - 平台规则（退货、保修、运费）、选购建议、功能介绍这类知识性问题，才查知识库
                - 原则：能用结构化工具精确获取的信息，就不要去知识库模糊检索；拿不准时先判断问题类型再选工具
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
