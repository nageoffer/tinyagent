package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

public class ApplyRefundTool implements Tool {

    @Override
    public String name() {
        return "applyRefund";
    }

    @Override
    public String description() {
        return "发起退款申请，返回退款单号和预计到账时间。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "orderId": {
                      "type": "string",
                      "description": "订单号"
                    },
                    "reason": {
                      "type": "string",
                      "description": "退款原因，需说明具体问题，如 质量问题：扫地机无法回充"
                    }
                  },
                  "required": ["orderId", "reason"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String orderId = ToolUtils.extractRequiredField(input, "orderId");
        if (orderId.isBlank()) {
            return ToolUtils.missingRequiredField("orderId");
        }

        String reason = ToolUtils.extractRequiredField(input, "reason");
        if (reason.isBlank()) {
            return ToolUtils.missingRequiredField("reason");
        }

        return "{\"success\":true,\"refundId\":\"RF20260629001\","
             + "\"orderId\":" + ToolUtils.toJsonString(orderId) + ","
             + "\"reason\":" + ToolUtils.toJsonString(reason) + ","
             + "\"message\":\"退款申请已提交，预计 1-3 个工作日到账\"}";
    }
}
