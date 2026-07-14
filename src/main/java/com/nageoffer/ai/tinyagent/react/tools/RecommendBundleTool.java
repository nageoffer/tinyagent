package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

public class RecommendBundleTool implements Tool {

    @Override
    public String name() {
        return "recommendBundle";
    }

    @Override
    public String description() {
        return "根据用户已有或感兴趣的商品，推荐 IoT 生态搭配组合（手机 + 手表 + 音箱等跨品类组合），返回搭配方案和组合价。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "baseProduct": {
                      "type": "string",
                      "description": "用户已有或感兴趣的基础商品名称，如 比特 Phone S1 手机"
                    },
                    "budget": {
                      "type": "number",
                      "description": "搭配总预算（元），可选"
                    },
                    "preferences": {
                      "type": "string",
                      "description": "用户偏好描述，如 运动健康监测、全屋智能控制"
                    }
                  },
                  "required": ["baseProduct"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String baseProduct = ToolUtils.extractRequiredField(input, "baseProduct");
        if (baseProduct.isBlank()) {
            return ToolUtils.missingRequiredField("baseProduct");
        }

        String lower = baseProduct.toLowerCase();

        if (lower.contains("phone") || lower.contains("手机")) {
            return "{\"baseProduct\":\"比特 Phone S1 手机\","
                    + "\"bundles\":["
                    + "{\"name\":\"运动健康套装\","
                    + "\"items\":[\"比特 Phone S1 手机（¥1999）\",\"比特 WatchFit 智能手表（¥599）\",\"比特 AirX 真无线耳机（¥399）\"],"
                    + "\"totalPrice\":2997,\"bundlePrice\":2799,"
                    + "\"saving\":198,\"scenario\":\"手机接收手表的运动和健康数据，耳机连接手机听歌跑步\"},"
                    + "{\"name\":\"全屋智能套装\","
                    + "\"items\":[\"比特 Phone S1 手机（¥1999）\",\"比特 SoundBox Mini 智能音箱（¥299）\",\"比特 WatchFit 智能手表（¥599）\"],"
                    + "\"totalPrice\":2897,\"bundlePrice\":2699,"
                    + "\"saving\":198,\"scenario\":\"手机作为智能家居控制中心，音箱做语音控制入口，手表随身提醒\"}"
                    + "]}";
        }

        if (lower.contains("watchfit") || lower.contains("手表")) {
            return "{\"baseProduct\":\"比特 WatchFit 智能手表\","
                    + "\"bundles\":["
                    + "{\"name\":\"运动伴侣套装\","
                    + "\"items\":[\"比特 WatchFit 智能手表（¥599）\",\"比特 AirX 真无线耳机（¥399）\"],"
                    + "\"totalPrice\":998,\"bundlePrice\":899,"
                    + "\"saving\":99,\"scenario\":\"手表记录运动数据，耳机配合运动听歌\"}"
                    + "]}";
        }

        if (lower.contains("soundbox") || lower.contains("音箱")) {
            return "{\"baseProduct\":\"比特 SoundBox Mini 智能音箱\","
                    + "\"bundles\":["
                    + "{\"name\":\"智能家居入门套装\","
                    + "\"items\":[\"比特 SoundBox Mini 智能音箱（¥299）\",\"比特 Phone S1 手机（¥1999）\"],"
                    + "\"totalPrice\":2298,\"bundlePrice\":2199,"
                    + "\"saving\":99,\"scenario\":\"音箱做语音控制中枢，手机做远程管理和设置\"}"
                    + "]}";
        }

        return "{\"baseProduct\":" + ToolUtils.toJsonString(baseProduct) + ","
                + "\"bundles\":[],\"message\":\"暂无该商品的搭配推荐方案\"}";
    }
}
