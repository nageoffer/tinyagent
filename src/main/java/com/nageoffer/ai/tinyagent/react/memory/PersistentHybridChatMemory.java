package com.nageoffer.ai.tinyagent.react.memory;

import com.nageoffer.ai.tinyagent.react.LlmClient;

import javax.sql.DataSource;
import java.util.List;

public class PersistentHybridChatMemory implements ChatMemory {

    private final JdbcChatMemory persistence;
    private final HybridChatMemory compression;

    public PersistentHybridChatMemory(DataSource dataSource, String sessionId,
                                       LlmClient llmClient, int recentSize, int summaryThreshold) {
        this.persistence = new JdbcChatMemory(dataSource, sessionId);
        this.compression = new HybridChatMemory(llmClient, recentSize, summaryThreshold);
        for (ChatMessage msg : persistence.messages()) {
            compression.add(msg);
        }
    }

    @Override
    public void add(ChatMessage message) {
        persistence.add(message);
        compression.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        return compression.messages();
    }

    @Override
    public void clear() {
        persistence.clear();
        compression.clear();
    }
}
