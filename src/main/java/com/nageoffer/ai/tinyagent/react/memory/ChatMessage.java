package com.nageoffer.ai.tinyagent.react.memory;

public record ChatMessage(Role role, String content, String toolCallId) {

    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage(Role.TOOL, content, toolCallId);
    }
}
