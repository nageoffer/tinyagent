package com.nageoffer.ai.tinyagent.react.memory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SessionManager {

    private final DataSource dataSource;

    public SessionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String createSession(String userId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sql = "INSERT INTO chat_session (session_id, user_id) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("创建会话失败", e);
        }
        return sessionId;
    }

    public List<ChatSession> listSessions(String userId) {
        String sql = "SELECT session_id, user_id, title, created_at, updated_at "
                + "FROM chat_session WHERE user_id = ? ORDER BY updated_at DESC";
        List<ChatSession> sessions = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new ChatSession(
                            rs.getString("session_id"),
                            rs.getString("user_id"),
                            rs.getString("title"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getTimestamp("updated_at").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询会话列表失败", e);
        }
        return sessions;
    }

    public void updateTitle(String sessionId, String title) {
        String sql = "UPDATE chat_session SET title = ? WHERE session_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新会话标题失败", e);
        }
    }

    public void expireSessions(int maxAgeDays) {
        String deleteMessages = "DELETE FROM chat_message WHERE session_id IN "
                + "(SELECT session_id FROM chat_session WHERE updated_at < NOW() - INTERVAL '" + maxAgeDays + " days')";
        String deleteSessions = "DELETE FROM chat_session WHERE updated_at < NOW() - INTERVAL '" + maxAgeDays + " days'";
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(deleteMessages)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteSessions)) {
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    System.out.println("[清理] 已删除 " + deleted + " 个过期会话");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("清理过期会话失败", e);
        }
    }
}
