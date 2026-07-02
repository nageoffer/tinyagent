package com.nageoffer.ai.tinyagent.react.memory;

import java.util.List;

public interface ChatMemory {

    void add(ChatMessage message);

    List<ChatMessage> messages();

    void clear();
}
