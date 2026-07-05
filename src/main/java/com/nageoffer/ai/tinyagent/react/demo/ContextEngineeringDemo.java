package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.EmbeddingClient;
import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.context.ObservationFolder;
import com.nageoffer.ai.tinyagent.react.context.ToolFilter;
import com.nageoffer.ai.tinyagent.react.memory.ChatMemory;
import com.nageoffer.ai.tinyagent.react.memory.LongTermMemoryRetriever;
import com.nageoffer.ai.tinyagent.react.memory.PersistentHybridChatMemory;
import com.nageoffer.ai.tinyagent.react.memory.PgKeyValueLongTermMemory;
import com.nageoffer.ai.tinyagent.react.memory.PgVectorLongTermMemory;
import com.nageoffer.ai.tinyagent.react.memory.SessionManager;
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
import java.util.Properties;

public class ContextEngineeringDemo {

    public static void main(String[] args) {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new QueryOrderTool());
        toolRegistry.register(new QueryLogisticsTool());
        toolRegistry.register(new ApplyRefundTool());
        toolRegistry.register(new SearchKnowledgeTool());
        toolRegistry.register(new GetCurrentTimeTool());

        Properties dotEnv = loadDotEnv();
        LlmClient llmClient = new LlmClient(
                setting(dotEnv, "TINYAGENT_API_URL",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_MODEL", "deepseek-v4-pro")
        );

        EmbeddingClient embeddingClient = new EmbeddingClient(
                setting(dotEnv, "TINYAGENT_EMBEDDING_URL",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_EMBEDDING_MODEL", "text-embedding-v3")
        );

        DataSource dataSource = createDataSource(dotEnv);
        SessionManager sessionManager = new SessionManager(dataSource);

        PgKeyValueLongTermMemory kvMemory = new PgKeyValueLongTermMemory(dataSource);
        PgVectorLongTermMemory vectorMemory = new PgVectorLongTermMemory(dataSource, embeddingClient);
        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(kvMemory, vectorMemory, 600);

        ToolFilter toolFilter = new ToolFilter(llmClient);
        ObservationFolder observationFolder = new ObservationFolder(200);

        String userId = "user_10086";
        String sessionId = sessionManager.createSession(userId);
        ChatMemory chatMemory = new PersistentHybridChatMemory(
                dataSource, sessionId, llmClient, 6, 12);

        ReActAgent agent = new ReActAgent(
                llmClient, toolRegistry, 10, 8000,
                chatMemory, retriever, toolFilter, observationFolder);

        System.out.println("========== 第 1 轮：查订单 ==========");
        agent.run("帮我查一下订单 88231 的物流", userId);

        System.out.println("\n\n========== 第 2 轮：退款 ==========");
        agent.run("那我要退款呢，这个扫地机不回充了", userId);

        System.out.println("\n\n========== 第 3 轮：推荐 ==========");
        agent.run("有没有新款扫地机推荐一下", userId);
    }

    private static DataSource createDataSource(Properties dotEnv) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(setting(dotEnv, "TINYAGENT_DB_URL",
                "jdbc:postgresql://localhost:5432/tinyagent"));
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
