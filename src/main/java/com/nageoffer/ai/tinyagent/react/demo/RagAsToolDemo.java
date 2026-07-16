package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.EmbeddingClient;
import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.rag.KnowledgeImporter;
import com.nageoffer.ai.tinyagent.react.rag.RagSearchTool;
import com.nageoffer.ai.tinyagent.react.tools.ApplyRefundTool;
import com.nageoffer.ai.tinyagent.react.tools.CompareProductsTool;
import com.nageoffer.ai.tinyagent.react.tools.GetCurrentTimeTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryLogisticsTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryOrderTool;
import com.nageoffer.ai.tinyagent.react.tools.RecommendBundleTool;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class RagAsToolDemo {

    public static void main(String[] args) {
        Properties dotEnv = loadDotEnv();

        DataSource dataSource = createDataSource(dotEnv);

        EmbeddingClient embeddingClient = new EmbeddingClient(
                setting(dotEnv, "TINYAGENT_EMBEDDING_URL",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_EMBEDDING_MODEL", "text-embedding-v3")
        );

        // 导入知识库（幂等：先清空再写入，重复跑不会重复累积）
        KnowledgeImporter importer = new KnowledgeImporter(dataSource, embeddingClient);
        importer.importFromResources("knowledge");

        LlmClient llmClient = new LlmClient(
                setting(dotEnv, "TINYAGENT_API_URL",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_MODEL", "qwen-plus")
        );

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new QueryOrderTool());
        toolRegistry.register(new QueryLogisticsTool());
        toolRegistry.register(new ApplyRefundTool());
        toolRegistry.register(new GetCurrentTimeTool());
        toolRegistry.register(new CompareProductsTool());
        toolRegistry.register(new RecommendBundleTool());
        toolRegistry.register(new RagSearchTool(dataSource, embeddingClient));

        ReActAgent agent = new ReActAgent(llmClient, toolRegistry);

        System.out.println("========== 场景一：退货政策咨询（应该查知识库）==========");
        String answer1 = agent.run("比特扫地机拆封后还能退吗？");
        System.out.println("[最终答复] " + answer1);

        System.out.println("\n========== 场景二：查订单状态（不应该查知识库）==========");
        String answer2 = agent.run("帮我查一下订单 88231 到了没");
        System.out.println("[最终答复] " + answer2);

        System.out.println("\n========== 场景三：混合场景（订单 + 知识库）==========");
        String answer3 = agent.run("我买的扫地机不回充了，能退吗？订单号 88231");
        System.out.println("[最终答复] " + answer3);
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
