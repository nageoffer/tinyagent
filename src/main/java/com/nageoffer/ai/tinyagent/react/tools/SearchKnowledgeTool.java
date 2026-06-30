package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;

public class SearchKnowledgeTool implements Tool {

    @Override
    public String name() {
        return "searchKnowledge";
    }

    @Override
    public String description() {
        return "检索售后知识库。输入：用户问题或关键词。返回：相关售后政策、处理规则或商品说明。";
    }

    @Override
    public String invoke(String input) {
        String query = input == null ? "" : input;
        if (query.contains("退") || query.contains("退款") || query.contains("七天")) {
            return "{\"matched\":\"七天无理由退货政策\","
                    + "\"content\":\"签收次日起 7 天内，商品外观和主要配件完整，可申请退货；质量问题需先进行售后检测。\"}";
        }
        if (query.contains("扫地机") || query.contains("不回充") || query.contains("维修")) {
            return "{\"matched\":\"扫地机售后处理规则\","
                    + "\"content\":\"扫地机无法回充可先尝试清洁充电触点、检查基站供电；若仍无法解决，可发起售后检测。\"}";
        }
        return "{\"matched\":\"通用客服规则\","
                + "\"content\":\"请先确认订单信息、商品状态和用户诉求，再选择退款、维修或物流查询等后续动作。\"}";
    }
}
