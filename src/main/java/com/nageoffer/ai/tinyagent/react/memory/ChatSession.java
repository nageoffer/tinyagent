package com.nageoffer.ai.tinyagent.react.memory;

import java.time.LocalDateTime;

public record ChatSession(
        String sessionId,
        String userId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
