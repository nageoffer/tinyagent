package com.nageoffer.ai.tinyagent.react.memory;

public record MemoryEntry(
        String key,
        String content,
        String userId,
        String type,
        long timestamp,
        double[] embedding
) {

    public static MemoryEntry of(String key, String content, String userId, String type) {
        return new MemoryEntry(key, content, userId, type, System.currentTimeMillis(), null);
    }
}
