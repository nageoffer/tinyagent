package com.nageoffer.ai.tinyagent.react;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ToolUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolUtils() {
    }

    public static String extractField(String input, String fieldName) {
        return extractField(input, fieldName, true);
    }

    public static String extractRequiredField(String input, String fieldName) {
        return extractField(input, fieldName, false);
    }

    public static String missingRequiredField(String fieldName) {
        return "{\"error\":" + toJsonString("缺少必填参数 " + fieldName) + "}";
    }

    public static String toJsonString(String value) {
        try {
            return MAPPER.writeValueAsString(value == null ? "" : value);
        } catch (JsonProcessingException e) {
            return "\"\"";
        }
    }

    private static String extractField(String input, String fieldName, boolean fallbackToRawInput) {
        if (input == null || input.isBlank() || fieldName == null || fieldName.isBlank()) {
            return "";
        }

        String trimmed = input.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = MAPPER.readTree(trimmed);
                JsonNode field = node.get(fieldName);
                if (field != null && !field.isNull()) {
                    return field.asText().trim();
                }
                return "";
            } catch (Exception ignored) {
                return fallbackToRawInput ? trimmed : "";
            }
        }

        return fallbackToRawInput ? trimmed : "";
    }
}
