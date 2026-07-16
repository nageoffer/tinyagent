package com.nageoffer.ai.tinyagent.react.rag;

import com.nageoffer.ai.tinyagent.react.EmbeddingClient;
import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RagSearchTool implements Tool {

    private static final double SIMILARITY_THRESHOLD = 0.5;

    private final DataSource dataSource;
    private final EmbeddingClient embeddingClient;
    private final int topK;

    public RagSearchTool(DataSource dataSource, EmbeddingClient embeddingClient) {
        this(dataSource, embeddingClient, 3);
    }

    public RagSearchTool(DataSource dataSource, EmbeddingClient embeddingClient, int topK) {
        this.dataSource = dataSource;
        this.embeddingClient = embeddingClient;
        this.topK = topK;
    }

    @Override
    public String name() {
        return "searchKnowledge";
    }

    @Override
    public String description() {
        return "检索比特严选知识库，查找售后政策、产品信息、选购指南、故障排查等内容。"
                + "当用户的问题涉及产品知识、平台规则或常见问题时使用。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "搜索内容，用自然语言描述要查找的信息"
                    }
                  },
                  "required": ["query"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String query = ToolUtils.extractField(input, "query");
        if (query.isBlank()) {
            return "{\"error\":\"缺少搜索内容\"}";
        }

        double[] queryVector = embeddingClient.embed(query);
        String vectorStr = toVectorString(queryVector);

        String sql = "SELECT source, content, "
                + "1 - (embedding <=> ?::vector) AS similarity "
                + "FROM knowledge_chunk "
                + "WHERE embedding IS NOT NULL "
                + "ORDER BY embedding <=> ?::vector "
                + "LIMIT ?";

        List<ChunkResult> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vectorStr);
            ps.setString(2, vectorStr);
            ps.setInt(3, topK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ChunkResult(
                            rs.getString("source"),
                            rs.getString("content"),
                            rs.getDouble("similarity")
                    ));
                }
            }
        } catch (SQLException e) {
            return "{\"error\":" + ToolUtils.toJsonString("知识库检索失败：" + e.getMessage()) + "}";
        }

        List<ChunkResult> filtered = results.stream()
                .filter(r -> r.similarity >= SIMILARITY_THRESHOLD)
                .toList();

        if (filtered.isEmpty()) {
            return "{\"query\":" + ToolUtils.toJsonString(query)
                    + ",\"matched\":0,\"message\":\"未找到足够相关的知识内容\"}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"query\":").append(ToolUtils.toJsonString(query)).append(",");
        sb.append("\"matched\":").append(filtered.size()).append(",");
        sb.append("\"chunks\":[");
        for (int i = 0; i < filtered.size(); i++) {
            if (i > 0) sb.append(",");
            ChunkResult r = filtered.get(i);
            sb.append("{\"source\":").append(ToolUtils.toJsonString(r.source)).append(",");
            sb.append("\"content\":").append(ToolUtils.toJsonString(r.content)).append(",");
            sb.append("\"similarity\":").append(String.format("%.4f", r.similarity)).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String toVectorString(double[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private record ChunkResult(String source, String content, double similarity) {}
}
