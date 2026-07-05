package com.nageoffer.ai.tinyagent.react.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ObservationFolder {

    private final int maxLength;
    private final ObjectMapper objectMapper;

    public ObservationFolder(int maxLength) {
        this.maxLength = maxLength;
        this.objectMapper = new ObjectMapper();
    }

    public String fold(String observation) {
        if (observation == null || observation.length() <= maxLength) {
            return observation;
        }

        String trimmed = observation.trim();
        if (trimmed.startsWith("{")) {
            String folded = foldJson(trimmed);
            if (folded != null) {
                return folded.length() <= maxLength ? folded : truncateAtBoundary(folded);
            }
        }

        return truncateAtBoundary(observation);
    }

    private String foldJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) return null;

            ObjectNode summary = objectMapper.createObjectNode();
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isValueNode()) {
                    summary.set(entry.getKey(), value);
                }
            });

            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncateAtBoundary(String text) {
        int cutoff = Math.min(text.length(), maxLength);
        for (int i = cutoff; i > cutoff / 2; i--) {
            char c = text.charAt(i - 1);
            if (c == ',' || c == '，' || c == '。' || c == '}') {
                return text.substring(0, i) + "...[已折叠]";
            }
        }
        return text.substring(0, cutoff) + "...[已折叠]";
    }
}
