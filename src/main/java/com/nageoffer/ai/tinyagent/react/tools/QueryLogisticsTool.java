package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

public class QueryLogisticsTool implements Tool {

    @Override
    public String name() {
        return "queryLogistics";
    }

    @Override
    public String description() {
        return "根据运单号查询物流轨迹，返回承运商、物流状态和轨迹详情。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "trackingNo": {
                      "type": "string",
                      "description": "快递运单号，如 SF1234567890"
                    }
                  },
                  "required": ["trackingNo"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String trackingNo = ToolUtils.extractField(input, "trackingNo");
        if ("SF1234567890".equals(trackingNo)) {
            return "{\"trackingNo\":\"SF1234567890\",\"carrier\":\"顺丰速运\","
                    + "\"status\":\"已签收\",\"traces\":["
                    + "{\"time\":\"2026-06-20 18:20:00\",\"desc\":\"快件已揽收\"},"
                    + "{\"time\":\"2026-06-21 09:10:00\",\"desc\":\"快件到达上海转运中心\"},"
                    + "{\"time\":\"2026-06-22 11:35:00\",\"desc\":\"快件已由本人签收\"}"
                    + "]}";
        }
        return "{\"error\":" + ToolUtils.toJsonString("未找到物流轨迹：" + trackingNo) + "}";
    }
}
