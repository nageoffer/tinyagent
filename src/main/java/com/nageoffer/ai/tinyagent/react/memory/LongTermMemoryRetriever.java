package com.nageoffer.ai.tinyagent.react.memory;

import java.util.List;

public class LongTermMemoryRetriever {

    private final PgKeyValueLongTermMemory kvMemory;
    private final PgVectorLongTermMemory vectorMemory;
    private final int maxTokens;

    public LongTermMemoryRetriever(PgKeyValueLongTermMemory kvMemory,
                                   PgVectorLongTermMemory vectorMemory,
                                   int maxTokens) {
        this.kvMemory = kvMemory;
        this.vectorMemory = vectorMemory;
        this.maxTokens = maxTokens;
    }

    public String buildMemoryContext(String userId, String userQuery) {
        StringBuilder context = new StringBuilder();
        int estimatedTokens = 0;

        List<MemoryEntry> profiles = kvMemory.retrieve(userId, "profile", 1);
        if (!profiles.isEmpty()) {
            String profileText = "用户画像：" + profiles.getFirst().content();
            context.append(profileText).append("\n");
            estimatedTokens += profileText.length();
        }

        List<MemoryEntry> relevant = vectorMemory.retrieve(userId, userQuery, 3);
        for (MemoryEntry entry : relevant) {
            String entryText = "交互记录：" + entry.content();
            if (estimatedTokens + entryText.length() > maxTokens) {
                break;
            }
            context.append(entryText).append("\n");
            estimatedTokens += entryText.length();
        }

        return context.toString();
    }
}
