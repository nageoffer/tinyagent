package com.nageoffer.ai.tinyagent.react.context;

import lombok.Getter;

public class ContextBudget {

    private static final double TOKENS_PER_CHAR = 1.0;

    private final int totalBudget;
    private final int memoryLimit;
    private final int toolDescLimit;
    private final int currentTurnReserve;

    private int systemPromptTokens = 0;
    private int memoryTokens = 0;
    private int toolDescTokens = 0;
    @Getter
    private int historyTokens = 0;
    private int currentTurnTokens = 0;

    public ContextBudget(int totalBudget) {
        this(totalBudget, 800, 2000, 2000);
    }

    public ContextBudget(int totalBudget, int memoryLimit,
                         int toolDescLimit, int currentTurnReserve) {
        this.totalBudget = totalBudget;
        this.memoryLimit = memoryLimit;
        this.toolDescLimit = toolDescLimit;
        this.currentTurnReserve = currentTurnReserve;
    }

    public void addSystemPrompt(String content) {
        systemPromptTokens += estimateTokens(content);
    }

    public boolean tryAddMemory(String content) {
        int tokens = estimateTokens(content);
        if (memoryTokens + tokens > memoryLimit) {
            return false;
        }
        memoryTokens += tokens;
        return true;
    }

    public boolean tryAddToolDescription(String content) {
        int tokens = estimateTokens(content);
        if (toolDescTokens + tokens > toolDescLimit) {
            return false;
        }
        toolDescTokens += tokens;
        return true;
    }

    public void addHistory(String content) {
        historyTokens += estimateTokens(content);
    }

    public void addCurrentTurn(String content) {
        currentTurnTokens += estimateTokens(content);
    }

    public int getHistoryBudget() {
        int used = systemPromptTokens + memoryTokens + toolDescTokens;
        return Math.max(0, totalBudget - used - currentTurnReserve);
    }

    public int getTotalUsed() {
        return systemPromptTokens + memoryTokens + toolDescTokens
                + historyTokens + currentTurnTokens;
    }

    public boolean isExceeded() {
        return getTotalUsed() >= totalBudget;
    }

    public String getReport() {
        return String.format(
                "系统提示词 %d + 长期记忆 %d + 工具描述 %d + 对话历史 %d + 当前轮次 %d = %d / %d",
                systemPromptTokens, memoryTokens, toolDescTokens,
                historyTokens, currentTurnTokens, getTotalUsed(), totalBudget);
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return (int) Math.ceil(text.length() * TOKENS_PER_CHAR);
    }
}
