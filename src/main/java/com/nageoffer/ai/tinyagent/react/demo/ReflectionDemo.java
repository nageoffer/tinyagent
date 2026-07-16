package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.plan.PlanAndExecuteAgent;
import com.nageoffer.ai.tinyagent.react.plan.Reflector;
import com.nageoffer.ai.tinyagent.react.tools.ApplyRefundTool;
import com.nageoffer.ai.tinyagent.react.tools.CompareProductsTool;
import com.nageoffer.ai.tinyagent.react.tools.GetCurrentTimeTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryLogisticsTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryOrderTool;
import com.nageoffer.ai.tinyagent.react.tools.RecommendBundleTool;
import com.nageoffer.ai.tinyagent.react.tools.SearchKnowledgeTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ReflectionDemo {

    public static void main(String[] args) {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new QueryOrderTool());
        toolRegistry.register(new QueryLogisticsTool());
        toolRegistry.register(new ApplyRefundTool());
        toolRegistry.register(new SearchKnowledgeTool());
        toolRegistry.register(new GetCurrentTimeTool());
        toolRegistry.register(new CompareProductsTool());
        toolRegistry.register(new RecommendBundleTool());

        Properties dotEnv = loadDotEnv();
        LlmClient llmClient = new LlmClient(
                setting(dotEnv, "TINYAGENT_API_URL",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_MODEL", "qwen-plus")
        );

        // 订单 99999 不存在，queryOrder 不抛错，只返回一个字段齐全的空壳：status 是"查询中"，还附一句"请稍后再试"
        // 数据没错、工具没报错，但压根没回答"到了没"——这种情况 try-catch 抓不到，正好看反思怎么处理
        String userMessage = "帮我查一下订单 99999 到了没";

        // 只演示反思本身：构造时多传一个 Reflector 即可
        // Reflector 拿步骤目标去校验结果，发现"查询中"根本没拿到真实订单状态，判定数据缺失、触发 REPLAN，改口如实告知用户
        Reflector reflector = new Reflector(llmClient);
        PlanAndExecuteAgent agent =
                new PlanAndExecuteAgent(llmClient, toolRegistry, 2, reflector);
        String answer = agent.run(userMessage);
        System.out.println("\n[最终结果] " + answer);
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
