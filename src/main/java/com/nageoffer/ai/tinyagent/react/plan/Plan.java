package com.nageoffer.ai.tinyagent.react.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * 一份完整的执行计划，包含用户原始目标和拆解出的步骤列表。
 * 由 Planner 创建，PlanAndExecuteAgent 驱动执行，重规划时合并已完成步骤与新计划
 */
public class Plan {

    /**
     * 用户的原始需求（即 userMessage），执行步骤和重规划时均从此字段读取
     */
    private final String goal;

    /**
     * 按执行顺序排列的步骤列表
     */
    private final List<PlanStep> steps;

    public Plan(String goal, List<PlanStep> steps) {
        this.goal = goal;
        this.steps = new ArrayList<>(steps);
    }

    public PlanStep nextPendingStep() {
        return steps.stream()
                .filter(s -> s.getStatus() == PlanStepStatus.PENDING)
                .findFirst()
                .orElse(null);
    }

    public boolean isAllDone() {
        return steps.stream()
                .allMatch(s -> s.getStatus() == PlanStepStatus.COMPLETED
                        || s.getStatus() == PlanStepStatus.FAILED);
    }

    public boolean hasFailedStep() {
        return steps.stream()
                .anyMatch(s -> s.getStatus() == PlanStepStatus.FAILED);
    }

    public String getProgressSummary() {
        StringBuilder sb = new StringBuilder();
        for (PlanStep step : steps) {
            String statusMark;
            switch (step.getStatus()) {
                case COMPLETED -> statusMark = "✓";
                case FAILED -> statusMark = "✗";
                case EXECUTING -> statusMark = "▶";
                default -> statusMark = "○";
            }
            sb.append(String.format("  %s Step %d: %s",
                    statusMark, step.getStepId(), step.getDescription()));
            if (step.getResult() != null) {
                String preview = step.getResult().length() > 80
                        ? step.getResult().substring(0, 80) + "..."
                        : step.getResult();
                sb.append(" → ").append(preview);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public int getNextStepId() {
        return steps.stream()
                .mapToInt(PlanStep::getStepId)
                .max()
                .orElse(0) + 1;
    }

    public String getGoal() {
        return goal;
    }

    public List<PlanStep> getSteps() {
        return steps;
    }
}
