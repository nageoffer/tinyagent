package com.nageoffer.ai.tinyagent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    public ArrayNode buildToolsJsonArray(ObjectMapper objectMapper) {
        ArrayNode toolsArray = objectMapper.createArrayNode();
        for (Tool tool : tools.values()) {
            ObjectNode toolNode = toolsArray.addObject();
            toolNode.put("type", "function");

            ObjectNode functionNode = toolNode.putObject("function");
            functionNode.put("name", tool.name());
            functionNode.put("description", tool.description());

            String params = tool.parameters();
            if (params != null && !params.isBlank()) {
                try {
                    functionNode.set("parameters", objectMapper.readTree(params));
                } catch (Exception e) {
                    functionNode.putObject("parameters").put("type", "object");
                }
            } else {
                functionNode.putObject("parameters").put("type", "object");
            }
        }
        return toolsArray;
    }
}
