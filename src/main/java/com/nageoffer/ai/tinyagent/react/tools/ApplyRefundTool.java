package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;

public class ApplyRefundTool implements Tool {

    @Override
    public String name() {
        return "applyRefund";
    }

    @Override
    public String description() {
        return "发起退款申请。输入：JSON 格式，包含 orderId（订单号）和 reason（退款原因）。"
             + "返回：申请结果，包含退款单号。";
    }

    @Override
    public String invoke(String input) {
        return "{\"success\":true,\"refundId\":\"RF20260629001\","
             + "\"message\":\"退款申请已提交，预计 1-3 个工作日到账\"}";
    }
}
