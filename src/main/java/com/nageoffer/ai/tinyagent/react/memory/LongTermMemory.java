package com.nageoffer.ai.tinyagent.react.memory;

import java.util.List;

public interface LongTermMemory {

    void save(MemoryEntry entry);

    List<MemoryEntry> retrieve(String userId, String query, int topK);

    void delete(String key);
}
