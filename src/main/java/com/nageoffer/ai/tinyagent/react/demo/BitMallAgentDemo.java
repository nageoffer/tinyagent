package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.memory.ChatMemory;
import com.nageoffer.ai.tinyagent.react.memory.ChatMessage;
import com.nageoffer.ai.tinyagent.react.memory.ChatSession;
import com.nageoffer.ai.tinyagent.react.memory.JdbcChatMemory;
import com.nageoffer.ai.tinyagent.react.memory.SessionManager;
import com.nageoffer.ai.tinyagent.react.memory.SessionTitleGenerator;
import com.nageoffer.ai.tinyagent.react.tools.ApplyRefundTool;
import com.nageoffer.ai.tinyagent.react.tools.GetCurrentTimeTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryLogisticsTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryOrderTool;
import com.nageoffer.ai.tinyagent.react.tools.SearchKnowledgeTool;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public class BitMallAgentDemo {

    public static void main(String[] args) {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new QueryOrderTool());
        toolRegistry.register(new QueryLogisticsTool());
        toolRegistry.register(new ApplyRefundTool());
        toolRegistry.register(new SearchKnowledgeTool());
        toolRegistry.register(new GetCurrentTimeTool());

        Properties dotEnv = loadDotEnv();
        LlmClient llmClient = new LlmClient(
                setting(dotEnv, "TINYAGENT_API_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_MODEL", "deepseek-v4-pro")
        );

        DataSource dataSource = createDataSource(dotEnv);
        SessionManager sessionManager = new SessionManager(dataSource);

        String userA = "user_10086";
        String userB = "user_10010";

        // ========== 用户 A 的会话 ==========
        System.out.println("========== 用户 A 的会话 ==========");
        String sessionA = sessionManager.createSession(userA);
        ChatMemory memoryA = new JdbcChatMemory(dataSource, sessionA);
        ReActAgent agentA = new ReActAgent(llmClient, toolRegistry, 10, 8000, memoryA);

        String firstAnswer = agentA.run("帮我查一下订单 88231 的物流到哪了");

        // 第一轮结束后自动生成会话标题
        SessionTitleGenerator titleGen = new SessionTitleGenerator(llmClient);
        String titleA = titleGen.generate("帮我查一下订单 88231 的物流到哪了", firstAnswer);
        sessionManager.updateTitle(sessionA, titleA);
        System.out.println("[会话标题] " + titleA);

        agentA.run("那我要退款呢，这个扫地机不回充了");

        // ========== 用户 B 的会话（完全隔离） ==========
        System.out.println("\n\n========== 用户 B 的会话 ==========");
        String sessionB = sessionManager.createSession(userB);
        ChatMemory memoryB = new JdbcChatMemory(dataSource, sessionB);
        ReActAgent agentB = new ReActAgent(llmClient, toolRegistry, 10, 8000, memoryB);

        String firstAnswerB = agentB.run("有什么智能音箱推荐吗，500 以内的");
        String titleB = titleGen.generate("有什么智能音箱推荐吗，500 以内的", firstAnswerB);
        sessionManager.updateTitle(sessionB, titleB);
        System.out.println("[会话标题] " + titleB);

        // ========== 查看用户 A 的历史会话 ==========
        System.out.println("\n\n========== 用户 A 的历史会话列表 ==========");
        List<ChatSession> sessionsA = sessionManager.listSessions(userA);
        for (ChatSession s : sessionsA) {
            System.out.println("会话 " + s.sessionId()
                    + " | 创建于 " + s.createdAt()
                    + " | 标题：" + (s.title() != null ? s.title() : "(未命名)"));
        }

        // ========== 加载用户 A 的旧会话，记忆还在 ==========
        System.out.println("\n\n========== 加载用户 A 的旧会话记忆 ==========");
        ChatMemory reloadedMemory = new JdbcChatMemory(dataSource, sessionA);
        List<ChatMessage> history = reloadedMemory.messages();
        System.out.println("历史消息共 " + history.size() + " 条：");
        for (ChatMessage msg : history) {
            String preview = msg.content() != null && msg.content().length() > 80
                    ? msg.content().substring(0, 80) + "..."
                    : msg.content();
            System.out.println("  [" + msg.role() + "] " + preview);
        }
    }

    private static DataSource createDataSource(Properties dotEnv) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(setting(dotEnv, "TINYAGENT_DB_URL", "jdbc:postgresql://localhost:5432/tinyagent"));
        ds.setUser(setting(dotEnv, "TINYAGENT_DB_USER", "postgres"));
        ds.setPassword(setting(dotEnv, "TINYAGENT_DB_PASSWORD", "postgres"));
        return ds;
    }

    private static Properties loadDotEnv() {
        Properties properties = new Properties();
        Path path = Path.of(".env");
        if (!Files.exists(path)) {
            return properties;
        }

        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("读取 .env 文件失败：" + path.toAbsolutePath(), e);
        }
    }

    private static String requiredSetting(Properties dotEnv, String key) {
        String value = setting(dotEnv, key, "");
        if (value.isBlank()) {
            throw new IllegalStateException("请在项目根目录 .env 文件中配置 " + key);
        }
        return value;
    }

    private static String setting(Properties dotEnv, String key, String defaultValue) {
        String dotEnvValue = dotEnv.getProperty(key);
        if (dotEnvValue != null && !dotEnvValue.isBlank()) {
            return dotEnvValue;
        }
        return defaultValue;
    }
}
