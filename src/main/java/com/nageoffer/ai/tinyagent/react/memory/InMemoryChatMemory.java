package com.nageoffer.ai.tinyagent.react.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryChatMemory implements ChatMemory {

    private final List<ChatMessage> messages = new ArrayList<>();

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public void clear() {
        messages.clear();
    }
}
