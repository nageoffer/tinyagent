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

    @Override
    public String invoke(String input) {
        String query = ToolUtils.extractField(input, "query");
        return "{\"query\":" + ToolUtils.toJsonString(query) + ","
                + "\"matched\":\"七天无理由退货政策\","
                + "\"content\":\"签收次日起 7 天内，商品外观和主要配件完整，"
                + "可申请退货；质量问题需先进行售后检测。\"}";
    }
}
