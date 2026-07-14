package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.plan.AgentRouter;
import com.nageoffer.ai.tinyagent.react.plan.PlanAndExecuteAgent;
import com.nageoffer.ai.tinyagent.react.skill.SkillRegistry;
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

public class CrossCategoryDemo {

    public static void main(String[] args) {
        // 注册全部 7 个基础工具（含新增的 compareProducts 和 recommendBundle）
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

        // 加载全部 4 个技能（含新增的 product-comparison 和 iot-bundle）
        SkillRegistry skillRegistry = new SkillRegistry();
        skillRegistry.loadFromDirectory(Path.of("src/main/resources/skills"));

        // 构建主 Agent 注册表：通用工具 + activate_skill
        ToolRegistry agentTools = skillRegistry.buildAgentRegistry(toolRegistry);

        // 场景 1：ReAct + Skill —— 单品类对比
        System.out.println("========== 场景 1：耳机对比（ReAct + Skill） ==========");
        ReActAgent reactAgent = new ReActAgent(llmClient, agentTools, 10, 8000);
        String answer1 = reactAgent.run(
                "比特 AirX 耳机和 BandPro 耳机哪个好？我主要通勤用");
        System.out.println("\n[最终结果] " + answer1);

        // 场景 2：ReAct + Skill —— IoT 搭配推荐
        System.out.println("\n\n========== 场景 2：IoT 搭配推荐（ReAct + Skill） ==========");
        String answer2 = reactAgent.run(
                "我刚买了比特 Phone S1，想配一些智能设备，主要想运动时用");
        System.out.println("\n[最终结果] " + answer2);

        // 场景 3：Plan-and-Execute —— 跨品类复合任务（对比 + 搭配 + 售后）
        System.out.println("\n\n========== 场景 3：跨品类复合任务（Plan-and-Execute） ==========");
        PlanAndExecuteAgent planAgent = new PlanAndExecuteAgent(llmClient, toolRegistry, 2);
        String answer3 = planAgent.run(
                "帮我对比一下比特 AirX 耳机和 BandPro 耳机，"
                + "另外我买的比特 Phone S1 手机想配一套运动装备，预算 1000 以内，"
                + "顺便帮我查一下订单 88231 到了没");
        System.out.println("\n[最终结果] " + answer3);
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
