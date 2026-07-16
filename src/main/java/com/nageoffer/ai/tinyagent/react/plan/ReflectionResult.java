package com.nageoffer.ai.tinyagent.react.plan;

/**
 * 反思评估结果
 */
public class ReflectionResult {

    public enum Verdict {
        // 执行结果满足步骤目标，继续下一步
        PASS,
        // 执行结果有偏差，可通过重试修正
        RETRY,
        // 当前步骤无法通过重试修复，需要调整后续计划
        REPLAN
    }

    private final Verdict verdict;
    private final String analysis;
    private final String suggestion;

    public ReflectionResult(Verdict verdict, String analysis, String suggestion) {
        this.verdict = verdict;
        this.analysis = analysis;
        this.suggestion = suggestion;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getAnalysis() {
        return analysis;
    }

    public String getSuggestion() {
        return suggestion;
    }
}
