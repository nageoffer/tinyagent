package com.nageoffer.ai.tinyagent.react;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentTests {

    @Test
    void runExecutesToolAndReturnsFinalAnswer() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "回显输入内容";
            }

            @Override
            public String invoke(String input) {
                return "{\"echo\":\"" + input + "\"}";
            }
        });

        ReActAgent agent = new ReActAgent(new ScriptedLlmClient(), registry);

        String answer = agent.run("测试一下");

        assertThat(answer).isEqualTo("工具已经返回 hello。");
    }

    @Test
    void registryReturnsObservationErrorWhenToolDoesNotExist() {
        ToolRegistry registry = new ToolRegistry();

        String observation = registry.execute(new Action("missingTool", "hello"));

        assertThat(observation).contains("未找到工具：missingTool");
    }

    @Test
    void runReturnsPlainLlmOutputWhenNoActionCanBeParsed() {
        PlainAnswerLlmClient llmClient = new PlainAnswerLlmClient();
        ReActAgent agent = new ReActAgent(llmClient, new ToolRegistry());

        String answer = agent.run("不需要工具的问题");

        assertThat(answer).isEqualTo("这是一个不需要调用工具的直接答复。");
        assertThat(llmClient.calls).isEqualTo(1);
    }

    private static class ScriptedLlmClient extends LlmClient {

        private int calls;

        ScriptedLlmClient() {
            super("http://localhost", "test-key", "test-model");
        }

        @Override
        public String chat(List<Map<String, String>> messages) {
            calls++;
            if (calls == 1) {
                return """
                        Thought: 我需要调用回显工具。
                        Action: echo
                        Action Input: hello
                        """;
            }

            assertThat(messages.get(messages.size() - 1).get("content")).contains("Observation:");
            assertThat(messages.get(messages.size() - 1).get("content")).contains("hello");
            return """
                    Thought: 已经拿到工具结果，可以回答。
                    Final Answer: 工具已经返回 hello。
                    """;
        }
    }

    private static class PlainAnswerLlmClient extends LlmClient {

        private int calls;

        PlainAnswerLlmClient() {
            super("http://localhost", "test-key", "test-model");
        }

        @Override
        public String chat(List<Map<String, String>> messages) {
            calls++;
            return "\n这是一个不需要调用工具的直接答复。\n";
        }
    }
}
