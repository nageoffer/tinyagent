package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.EmbeddingClient;
import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.memory.ChatMemory;
import com.nageoffer.ai.tinyagent.react.memory.ExtractedProfile;
import com.nageoffer.ai.tinyagent.react.memory.PersistentHybridChatMemory;
import com.nageoffer.ai.tinyagent.react.memory.PgKeyValueLongTermMemory;
import com.nageoffer.ai.tinyagent.react.memory.LongTermMemoryRetriever;
import com.nageoffer.ai.tinyagent.react.memory.MemoryEntry;
import com.nageoffer.ai.tinyagent.react.memory.SessionManager;
import com.nageoffer.ai.tinyagent.react.memory.SessionTitleGenerator;
import com.nageoffer.ai.tinyagent.react.memory.UserProfileExtractor;
import com.nageoffer.ai.tinyagent.react.memory.PgVectorLongTermMemory;
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

public class LongTermMemoryDemo {

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

        EmbeddingClient embeddingClient = new EmbeddingClient(
                setting(dotEnv, "TINYAGENT_EMBEDDING_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_EMBEDDING_MODEL", "text-embedding-v3")
        );

        DataSource dataSource = createDataSource(dotEnv);
        SessionManager sessionManager = new SessionManager(dataSource);

        PgKeyValueLongTermMemory kvMemory = new PgKeyValueLongTermMemory(dataSource);
        PgVectorLongTermMemory vectorMemory = new PgVectorLongTermMemory(dataSource, embeddingClient);
        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(kvMemory, vectorMemory, 600);
        UserProfileExtractor extractor = new UserProfileExtractor(llmClient);

        String userId = "user_10086";

        // ========== 第一次会话 ==========
        System.out.println("========== 第一次会话 ==========");
        String session1Id = sessionManager.createSession(userId);
        ChatMemory session1Memory = new PersistentHybridChatMemory(
                dataSource, session1Id, llmClient, 6, 12);
        ReActAgent agent1 = new ReActAgent(
                llmClient, toolRegistry, 10, 8000, session1Memory, retriever);

        String firstAnswer = agent1.run("帮我查一下订单 88231 的物流", userId);

        SessionTitleGenerator titleGen = new SessionTitleGenerator(llmClient);
        String title1 = titleGen.generate("帮我查一下订单 88231 的物流", firstAnswer);
        sessionManager.updateTitle(session1Id, title1);
        System.out.println("[会话标题] " + title1);

        agent1.run("那我要退款呢，这个扫地机不回充了", userId);
        agent1.run("退款多久到账", userId);

        // 会话结束，提取长期记忆
        System.out.println("\n[会话结束] 提取用户画像和关键信息...");
        ExtractedProfile profile = extractor.extract(session1Memory.messages());
        System.out.println("[提取结果] 画像：" + profile.userProfile());
        System.out.println("[提取结果] 记录：" + profile.interactionRecord());

        if (profile.userProfile() != null) {
            kvMemory.save(MemoryEntry.of(
                    "profile:" + userId, profile.userProfile(), userId, "USER_PROFILE"));
        }
        if (profile.interactionRecord() != null) {
            vectorMemory.save(MemoryEntry.of(
                    "record:" + userId + ":" + System.currentTimeMillis(),
                    profile.interactionRecord(), userId, "INTERACTION_RECORD"));
        }

        // ========== 第二次会话（模拟第二天） ==========
        System.out.println("\n\n========== 第二次会话（第二天） ==========");
        String session2Id = sessionManager.createSession(userId);
        ChatMemory session2Memory = new PersistentHybridChatMemory(
                dataSource, session2Id, llmClient, 6, 12);
        ReActAgent agent2 = new ReActAgent(
                llmClient, toolRegistry, 10, 8000, session2Memory, retriever);

        String firstAnswer2 = agent2.run("昨天那个退款到账了吗", userId);

        String title2 = titleGen.generate("昨天那个退款到账了吗", firstAnswer2);
        sessionManager.updateTitle(session2Id, title2);
        System.out.println("[会话标题] " + title2);

        agent2.run("再帮我推荐一个扫地机呗，要比之前那个好的", userId);
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
