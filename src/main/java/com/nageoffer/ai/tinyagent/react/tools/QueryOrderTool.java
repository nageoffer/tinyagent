package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

public class QueryOrderTool implements Tool {

    @Override
    public String name() {
        return "queryOrder";
    }

    @Override
    public String description() {
        return "查询订单详情，返回商品名、价格、下单时间、签收时间、订单状态、运单号。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "orderId": {
                      "type": "string",
                      "description": "订单号，如 88231"
                    }
                  },
                  "required": ["orderId"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String orderId = ToolUtils.extractField(input, "orderId");
        if ("88231".equals(orderId)) {
            return "{\"orderId\":\"88231\",\"product\":\"比特 S10 Pro 扫地机\","
                 + "\"price\":1999,\"orderTime\":\"2026-06-20\","
                 + "\"signTime\":\"2026-06-22\",\"status\":\"已签收\","
                 + "\"trackingNo\":\"SF1234567890\"}";
        }
        // 99999 模拟一个信息还在同步的订单：不报错，返回一个字段齐全却没真正回答"到了没"的占位壳
        if ("99999".equals(orderId)) {
            return "{\"orderId\":\"99999\",\"status\":\"查询中\","
                 + "\"remark\":\"该订单信息正在同步，请稍后再试\"}";
        }
        return "{\"error\":" + ToolUtils.toJsonString("订单不存在：" + orderId) + "}";
    }
}
