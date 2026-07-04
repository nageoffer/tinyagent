package com.nageoffer.ai.tinyagent.react.memory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JdbcChatMemory implements ChatMemory {

    private final DataSource dataSource;
    private final String sessionId;

    public JdbcChatMemory(DataSource dataSource, String sessionId) {
        this.dataSource = dataSource;
        this.sessionId = sessionId;
    }

    @Override
    public void add(ChatMessage message) {
        String sql = "INSERT INTO chat_message (session_id, role, content, tool_call_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, message.role().name());
            ps.setString(3, message.content());
            ps.setString(4, message.toolCallId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("写入消息失败", e);
        }

        String updateSql = "UPDATE chat_session SET updated_at = CURRENT_TIMESTAMP WHERE session_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[警告] 更新会话时间失败：" + e.getMessage());
        }
    }

    @Override
    public List<ChatMessage> messages() {
        String sql = "SELECT role, content, tool_call_id FROM chat_message "
                + "WHERE session_id = ? ORDER BY id ASC";
        List<ChatMessage> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ChatMessage.Role role = ChatMessage.Role.valueOf(rs.getString("role"));
                    String content = rs.getString("content");
                    String toolCallId = rs.getString("tool_call_id");
                    result.add(new ChatMessage(role, content, toolCallId));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取消息失败", e);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void clear() {
        String sql = "DELETE FROM chat_message WHERE session_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("清空消息失败", e);
        }
    }

    public String getSessionId() {
        return sessionId;
    }
}
