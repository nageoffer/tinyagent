package com.nageoffer.ai.tinyagent.react.memory;

import com.nageoffer.ai.tinyagent.react.EmbeddingClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PgVectorLongTermMemory implements LongTermMemory {

    private final DataSource dataSource;
    private final EmbeddingClient embeddingClient;

    public PgVectorLongTermMemory(DataSource dataSource, EmbeddingClient embeddingClient) {
        this.dataSource = dataSource;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void save(MemoryEntry entry) {
        double[] embedding = embeddingClient.embed(entry.content());
        String vectorStr = toVectorString(embedding);

        String sql = "INSERT INTO memory_entry (key, content, user_id, type, embedding) "
                + "VALUES (?, ?, ?, ?, ?::vector) "
                + "ON CONFLICT (key) DO UPDATE SET content = EXCLUDED.content, "
                + "embedding = EXCLUDED.embedding, created_at = CURRENT_TIMESTAMP";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.key());
            ps.setString(2, entry.content());
            ps.setString(3, entry.userId());
            ps.setString(4, entry.type());
            ps.setString(5, vectorStr);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("保存记忆失败", e);
        }
    }

    @Override
    public List<MemoryEntry> retrieve(String userId, String query, int topK) {
        double[] queryVector = embeddingClient.embed(query);
        String vectorStr = toVectorString(queryVector);

        String sql = "SELECT key, content, user_id, type, created_at "
                + "FROM memory_entry "
                + "WHERE user_id = ? AND embedding IS NOT NULL "
                + "ORDER BY embedding <=> ?::vector "
                + "LIMIT ?";
        List<MemoryEntry> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, vectorStr);
            ps.setInt(3, topK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new MemoryEntry(
                            rs.getString("key"),
                            rs.getString("content"),
                            rs.getString("user_id"),
                            rs.getString("type"),
                            rs.getTimestamp("created_at").getTime(),
                            null
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("检索记忆失败", e);
        }
        return results;
    }

    @Override
    public void delete(String key) {
        String sql = "DELETE FROM memory_entry WHERE key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除记忆失败", e);
        }
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
}
