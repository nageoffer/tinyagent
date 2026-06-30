package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;

public class QueryLogisticsTool implements Tool {

    @Override
    public String name() {
        return "queryLogistics";
    }

    @Override
    public String description() {
        return "查询物流轨迹。输入：运单号（如 SF1234567890）。返回：物流公司、当前状态和轨迹列表。";
    }

    @Override
    public String invoke(String input) {
        String trackingNo = input.trim();
        if ("SF1234567890".equalsIgnoreCase(trackingNo)) {
            return "{\"trackingNo\":\"SF1234567890\",\"carrier\":\"顺丰速运\","
                    + "\"status\":\"已签收\",\"traces\":["
                    + "{\"time\":\"2026-06-20 18:20:00\",\"desc\":\"快件已揽收\"},"
                    + "{\"time\":\"2026-06-21 09:10:00\",\"desc\":\"快件到达上海转运中心\"},"
                    + "{\"time\":\"2026-06-22 11:35:00\",\"desc\":\"快件已由本人签收\"}"
                    + "]}";
        }
        return "{\"error\":\"未找到物流轨迹：" + trackingNo + "\"}";
    }
}
