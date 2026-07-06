package com.nageoffer.ai.tinyagent.react.plan;

/**
 * 计划中的一个执行步骤。
 * 由 Planner 生成，交给 Executor 逐步执行，执行结果回填到 result 字段
 */
public class PlanStep {

    /**
     * 步骤编号，从 1 开始递增，重规划时从当前最大编号继续
     */
    private final int stepId;

    /**
     * 这一步要做什么，自然语言描述（如"查询订单 88231 的详情"）
     */
    private final String description;

    /**
     * Planner 建议使用的工具名（如 "queryOrder"），为 null 表示纯文本综合步骤
     */
    private final String toolHint;

    /**
     * 当前执行状态：PENDING → EXECUTING → COMPLETED / FAILED
     */
    private PlanStepStatus status;

    /**
     * 执行结果（成功时为 Executor 返回的文本，失败时为错误原因）
     */
    private String result;

    public PlanStep(int stepId, String description, String toolHint) {
        this.stepId = stepId;
        this.description = description;
        this.toolHint = toolHint;
        this.status = PlanStepStatus.PENDING;
    }

    public void markCompleted(String result) {
        this.status = PlanStepStatus.COMPLETED;
        this.result = result;
    }

    public void markFailed(String reason) {
        this.status = PlanStepStatus.FAILED;
        this.result = reason;
    }

    public int getStepId() {
        return stepId;
    }

    public String getDescription() {
        return description;
    }

    public String getToolHint() {
        return toolHint;
    }

    public PlanStepStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStepStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }
}
