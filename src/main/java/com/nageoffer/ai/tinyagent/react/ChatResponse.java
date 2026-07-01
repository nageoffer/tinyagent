package com.nageoffer.ai.tinyagent.react;

import java.util.List;

public record ChatResponse(String content, List<ToolCallInfo> toolCalls, String finishReason) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
