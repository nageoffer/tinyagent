package com.nageoffer.ai.tinyagent.react;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        tools.put(tool.name(), tool);
    }

    public String execute(Action action) {
        if (action == null || action.toolName() == null || action.toolName().isBlank()) {
            return "{\"error\":\"未解析到可执行的工具名称\"}";
        }

        Tool tool = tools.get(action.toolName());
        if (tool == null) {
            return "{\"error\":\"未找到工具：" + action.toolName() + "\"}";
        }
        return tool.invoke(action.toolInput() == null ? "" : action.toolInput());
    }

    public String buildToolList() {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Tool tool : tools.values()) {
            sb.append(index++)
                    .append(". ")
                    .append(tool.name())
                    .append(" - ")
                    .append(tool.description())
                    .append("\n");
        }
        return sb.toString();
    }
}
