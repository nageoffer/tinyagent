package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GetCurrentTimeTool implements Tool {

    @Override
    public String name() {
        return "getCurrentTime";
    }

    @Override
    public String description() {
        return "获取当前日期时间。无需输入参数，返回当前时间的 JSON。";
    }

    @Override
    public String invoke(String input) {
        String now = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        return "{\"currentTime\":\"" + now + "\"}";
    }
}
