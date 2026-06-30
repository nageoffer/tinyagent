package com.nageoffer.ai.tinyagent.react;

public interface Tool {

    String name();

    String description();

    String invoke(String input);
}
