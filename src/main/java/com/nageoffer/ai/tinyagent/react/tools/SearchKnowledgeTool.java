package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

public class SearchKnowledgeTool implements Tool {

    @Override
    public String name() {
        return "searchKnowledge";
    }

    @Override
    public String description() {
        return "检索比特严选知识库，返回匹配的售后政策、常见问题或产品信息。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "搜索关键词或问题，如 扫地机 退货政策"
                    }
                  },
                  "required": ["query"]
                }""";
    }

    // 当前系列以 Agent 流程为主，RAG 检索仅提供 Mock 数据，按关键词返回预设结果
    @Override
    public String invoke(String input) {
        String query = ToolUtils.extractField(input, "query");
        String lowerQuery = query.toLowerCase();

        if (lowerQuery.contains("扫地机") && (lowerQuery.contains("推荐")
                || lowerQuery.contains("老人") || lowerQuery.contains("操作")
                || lowerQuery.contains("2000") || lowerQuery.contains("产品"))) {
            return "{\"query\":" + ToolUtils.toJsonString(query) + ","
                    + "\"matched\":\"扫地机选购指南\","
                    + "\"content\":\"比特 S10 Lite 扫地机（¥1599）：一键启停，语音播报，"
                    + "自动回充，适合老年人使用；比特 S10 Pro 扫地机（¥1999）：激光导航，"
                    + "App 远程控制，自动集尘，适合年轻家庭。两款均支持 7 天无理由退货。\"}";
        }

        if (lowerQuery.contains("耳机")) {
            return "{\"query\":" + ToolUtils.toJsonString(query) + ","
                    + "\"matched\":\"耳机产品列表\","
                    + "\"content\":\"比特 AirX 真无线耳机（¥399）：主动降噪，30 小时续航；"
                    + "比特 BandPro 头戴式耳机（¥699）：Hi-Res 认证，可折叠设计。\"}";
        }

        return "{\"query\":" + ToolUtils.toJsonString(query) + ","
                + "\"matched\":\"七天无理由退货政策\","
                + "\"content\":\"签收次日起 7 天内，商品外观和主要配件完整，"
                + "可申请退货；质量问题需先进行售后检测。\"}";
    }
}
