# TinyAgent

TinyAgent 是一个用 Java 实现的极简 ReAct Agent 示例项目。它演示了如何让大模型按照 `Thought -> Action -> Observation -> Final Answer` 的方式完成多轮推理，并在推理过程中调用本地工具。

当前示例场景是“比特严选智能客服”：Agent 可以查询订单、查询物流、检索售后知识库、发起退款申请，以及获取当前时间。

## 功能特性

- 极简 ReAct 循环：模型输出行动指令，本地工具执行后把结果作为 Observation 回传给模型。
- 可插拔工具接口：实现 `Tool` 接口并注册到 `ToolRegistry` 即可扩展能力。
- 兼容 Chat Completions 风格接口：默认配置使用阿里云 DashScope 兼容模式接口，也可以替换为其他兼容接口。
- 内置电商客服示例工具：订单查询、物流查询、退款申请、知识库检索、当前时间。
- 带单元测试：覆盖工具调用、缺失工具处理、无工具直答等核心行为。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Maven Wrapper
- OkHttp
- Jackson
- JUnit 5
- Lombok

## 项目结构

```text
.
├── pom.xml
├── README.md
├── .env.example
└── src
    ├── main
    │   ├── java/com/nageoffer/ai/tinyagent
    │   │   ├── TinyagentApplication.java
    │   │   └── react
    │   │       ├── ReActAgent.java
    │   │       ├── LlmClient.java
    │   │       ├── Tool.java
    │   │       ├── ToolRegistry.java
    │   │       ├── demo/BitMallAgentDemo.java
    │   │       └── tools
    │   └── resources/application.yaml
    └── test
```

核心类说明：

- `ReActAgent`：执行 ReAct 推理循环，解析模型输出中的 `Action` 和 `Final Answer`。
- `LlmClient`：调用兼容 Chat Completions 的大模型接口。
- `Tool`：本地工具接口。
- `ToolRegistry`：工具注册与调用入口。
- `BitMallAgentDemo`：命令行演示入口。

## 快速开始

### 1. 准备环境

确认本机已安装 JDK 21：

```bash
java -version
```

如果当前 shell 默认不是 Java 21，请先切换 `JAVA_HOME`，例如 macOS 可执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

项目已包含 Maven Wrapper，不需要额外安装 Maven。

### 2. 配置大模型接口

复制示例配置：

```bash
cp .env.example .env
```

编辑 `.env`：

```properties
TINYAGENT_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
TINYAGENT_API_KEY=your-api-key
TINYAGENT_MODEL=deepseek-v4-pro
```

配置项说明：

- `TINYAGENT_API_URL`：兼容 Chat Completions 的接口地址。
- `TINYAGENT_API_KEY`：接口访问密钥。
- `TINYAGENT_MODEL`：模型名称。

> 当前 demo 从项目根目录的 `.env` 文件读取配置。

### 3. 运行测试

```bash
./mvnw test
```

### 4. 运行客服 Agent demo

推荐直接在 IDE 中运行：

```text
src/main/java/com/nageoffer/ai/tinyagent/react/demo/BitMallAgentDemo.java
```

也可以使用 Maven 运行 main 方法：

```bash
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.nageoffer.ai.tinyagent.react.demo.BitMallAgentDemo
```

默认用户问题是：

```text
我上周买的扫地机不回充了，修不好我想退。订单号 88231。
```

运行后可以在控制台看到每一轮推理、工具调用和最终答复。示例订单 `88231` 会关联到运单号 `SF1234567890`。

## 工作原理

`ReActAgent` 会先构造系统提示词，把所有已注册工具的名称和描述告诉模型。模型必须按下面格式输出：

```text
Thought: <思考过程>
Action: <工具名>
Action Input: <工具入参>
```

Agent 解析到 `Action` 后，通过 `ToolRegistry` 执行对应工具，并把工具返回值追加为：

```text
Observation: <工具结果>
```

当模型认为信息足够时，输出：

```text
Thought: <总结>
Final Answer: <最终答复>
```

Agent 解析 `Final Answer` 后结束本次任务。

## 内置工具

| 工具名 | 作用 | 示例输入 |
| --- | --- | --- |
| `queryOrder` | 查询订单详情 | `88231` |
| `queryLogistics` | 查询物流轨迹 | `SF1234567890` |
| `applyRefund` | 发起退款申请 | `{"orderId":"88231","reason":"不想要了"}` |
| `searchKnowledge` | 检索售后知识库 | `七天无理由退货` |
| `getCurrentTime` | 获取当前时间 | 空 |

这些工具目前使用本地模拟数据，适合用于学习 Agent 调用链路。接入真实业务时，可以在工具实现中调用数据库、HTTP 服务、RPC 服务或其他内部系统。

## 扩展工具

新增工具只需要三步：

1. 实现 `Tool` 接口。

```java
public class QueryCouponTool implements Tool {

    @Override
    public String name() {
        return "queryCoupon";
    }

    @Override
    public String description() {
        return "查询用户优惠券。输入：用户 ID。返回：可用优惠券列表。";
    }

    @Override
    public String invoke(String input) {
        return "{\"coupons\":[]}";
    }
}
```

2. 在 `ToolRegistry` 中注册工具。

```java
ToolRegistry toolRegistry = new ToolRegistry();
toolRegistry.register(new QueryCouponTool());
```

3. 确保 `description()` 写清楚输入格式和返回内容。Agent 会把工具描述直接放进系统提示词，描述越准确，模型越容易正确调用。

## 注意事项

- 当前版本是教学和 demo 项目，内置工具返回的是模拟数据。
- `BitMallAgentDemo` 是命令行入口；`TinyagentApplication` 目前只启动 Spring Boot 应用上下文，没有暴露 HTTP 接口。
- `ReActAgent` 最多执行 10 轮推理，超过后会返回失败提示。
- `Action` 解析基于固定文本格式，请确保模型输出遵循提示词中的格式。
- `.env` 不应提交到代码仓库，请只提交 `.env.example`。

## License

暂无。
