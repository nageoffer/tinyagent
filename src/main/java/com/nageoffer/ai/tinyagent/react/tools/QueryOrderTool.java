package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;

public class QueryOrderTool implements Tool {

    @Override
    public String name() {
        return "queryOrder";
    }

    @Override
    public String description() {
        return "查询订单详情。输入：订单号（如 88231）。"
             + "返回：商品名、下单时间、签收时间、订单状态、运单号。";
    }

    @Override
    public String invoke(String input) {
        String orderId = input.trim();
        if ("88231".equals(orderId)) {
            return "{\"orderId\":\"88231\",\"product\":\"比特 S10 Pro 扫地机\","
                 + "\"price\":1999,\"orderTime\":\"2026-06-20\","
                 + "\"signTime\":\"2026-06-22\",\"status\":\"已签收\","
                 + "\"trackingNo\":\"SF1234567890\"}";
        }
        return "{\"error\":\"订单不存在：" + orderId + "\"}";
    }
}
