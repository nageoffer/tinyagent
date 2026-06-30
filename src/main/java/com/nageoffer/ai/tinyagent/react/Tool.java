package com.nageoffer.ai.tinyagent.react;

public interface Tool {

    String name();

    String description();

    default String parameters() {
        return "";
    }

    String invoke(String input);
}
