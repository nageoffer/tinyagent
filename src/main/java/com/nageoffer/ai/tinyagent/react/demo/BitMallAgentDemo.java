package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.memory.ChatMemory;
import com.nageoffer.ai.tinyagent.react.memory.ChatMessage;
import com.nageoffer.ai.tinyagent.react.memory.HybridChatMemory;
import com.nageoffer.ai.tinyagent.react.tools.ApplyRefundTool;
import com.nageoffer.ai.tinyagent.react.tools.GetCurrentTimeTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryLogisticsTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryOrderTool;
import com.nageoffer.ai.tinyagent.react.tools.SearchKnowledgeTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

        ChatMemory memory = new HybridChatMemory(llmClient, 6, 10);
        ReActAgent agent = new ReActAgent(llmClient, toolRegistry, 10, 8000, memory);

        System.out.println("========== 第 1 轮对话 ==========");
        String answer1 = agent.run("帮我查一下订单 88231 的物流到哪了");
        System.out.println("\n[最终结果] " + answer1);

        System.out.println("\n\n========== 第 2 轮对话 ==========");
        String answer2 = agent.run("那我要退款呢，这个扫地机不回充了");
        System.out.println("\n[最终结果] " + answer2);

        System.out.println("\n\n========== 第 3 轮对话 ==========");
        String answer3 = agent.run("退款多久到账？");
        System.out.println("\n[最终结果] " + answer3);

        System.out.println("\n\n========== 第 4 轮对话 ==========");
        String answer4 = agent.run("你们有没有新款扫地机推荐一下");
        System.out.println("\n[最终结果] " + answer4);

        System.out.println("\n\n========== 第 5 轮对话 ==========");
        String answer5 = agent.run("S20 Pro 多少钱");
        System.out.println("\n[最终结果] " + answer5);

        System.out.println("\n\n========== 第 6 轮对话 ==========");
        String answer6 = agent.run("对了，刚才那个退款编号是多少来着");
        System.out.println("\n[最终结果] " + answer6);

        System.out.println("\n\n===== 当前记忆状态 =====");
        for (ChatMessage msg : memory.messages()) {
            String preview = msg.content().length() > 80
                    ? msg.content().substring(0, 80) + "..."
                    : msg.content();
            System.out.println("[" + msg.role() + "] " + preview);
        }
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
