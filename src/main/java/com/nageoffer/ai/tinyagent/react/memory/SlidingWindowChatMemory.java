package com.nageoffer.ai.tinyagent.react.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SlidingWindowChatMemory implements ChatMemory {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final int maxSize;

    public SlidingWindowChatMemory(int maxSize) {
        if (maxSize < 2) {
            throw new IllegalArgumentException("窗口大小至少为 2（一轮对话需要 user + assistant）");
        }
        this.maxSize = maxSize;
    }

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
        while (messages.size() > maxSize) {
            messages.remove(0);
        }
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
