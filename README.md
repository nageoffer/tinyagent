# TinyAgent

TinyAgent 是一个用 Java 实现的轻量级 Agent 教学项目。它以“比特严选智能客服”为示例场景，演示大模型如何通过 Chat Completions `tools/tool_calls` 调用本地工具，并逐步扩展到 ReAct、Plan-and-Execute、会话记忆、长期记忆、上下文工程和 Skill 技能机制。

项目配套文档对应：[nageoffer/ai-handbook](https://github.com/nageoffer/ai-handbook)。

当前示例中的 Agent 可以查询订单、查询物流、检索售后知识库、发起退款申请、获取当前时间，并围绕多轮客服会话进行记忆管理与上下文压缩。

## 功能特性

- ReAct Agent：基于 `tools/tool_calls` 执行“模型决策 -> 工具调用 -> Observation 回传 -> 最终答复”的循环。
- Plan-and-Execute：先由 Planner 拆解复杂任务，再逐步执行计划，失败时支持重规划。
- 可插拔工具系统：实现 `Tool` 接口并注册到 `ToolRegistry`，即可把业务能力暴露给 Agent。
- Function Calling 参数描述：工具可通过 `parameters()` 提供 JSON Schema，提升模型调用工具的稳定性。
- Skill 技能机制：用 Markdown + YAML Front Matter 定义有作用域的技能指令和工具子集，支持渐进式工具展现。
- 短期会话记忆：支持内存记忆、滑动窗口记忆、摘要压缩记忆、混合记忆和 JDBC 持久化记忆。
- 长期记忆：支持 PostgreSQL KV 记忆、pgvector 向量记忆、用户画像提取和跨会话检索。
- 上下文工程：包含 Token 预算、工具筛选、Observation 折叠、重复工具调用检测和无进展检测。
- 电商客服 Mock 工具：内置订单、物流、退款、知识库和时间工具，便于专注学习 Agent 流程。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Maven Wrapper
- OkHttp
- Jackson / Jackson YAML
- PostgreSQL JDBC / pgvector
- Lombok
- JUnit 5

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
    │   │       ├── EmbeddingClient.java
    │   │       ├── Tool.java
    │   │       ├── ToolRegistry.java
    │   │       ├── context/
    │   │       ├── demo/
    │   │       ├── memory/
    │   │       ├── plan/
    │   │       ├── skill/
    │   │       └── tools/
    │   └── resources
    │       ├── application.yaml
    │       ├── schema.sql
    │       └── skills/
    └── test
```

核心模块说明：

| 模块 | 说明 |
| --- | --- |
| `react` | ReAct Agent、LLM 客户端、工具接口、工具注册表等基础能力 |
| `react.tools` | 比特严选客服场景的本地 Mock 工具 |
| `react.plan` | Planner、Plan、PlanStep、Plan-and-Execute Agent 和路由器 |
| `react.memory` | 会话记忆、持久化会话、长期记忆、画像提取和会话标题生成 |
| `react.context` | 上下文预算、工具筛选、Observation 折叠 |
| `react.skill` | Skill 加载、注册、适配和执行 |
| `react.demo` | 不同能力的命令行演示入口 |
| `resources/skills` | Markdown 技能定义文件 |
| `resources/schema.sql` | PostgreSQL / pgvector 表结构 |

## 快速开始

### 1. 准备环境

确认本机已安装 JDK 21：

```bash
java -version
```

如果当前 shell 默认不是 Java 21，请先切换 `JAVA_HOME`：

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
TINYAGENT_EMBEDDING_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
TINYAGENT_EMBEDDING_MODEL=text-embedding-v3
```

配置项说明：

| 配置项 | 说明 |
| --- | --- |
| `TINYAGENT_API_URL` | 兼容 Chat Completions 的接口地址 |
| `TINYAGENT_API_KEY` | 大模型接口访问密钥 |
| `TINYAGENT_MODEL` | Chat 模型名称 |
| `TINYAGENT_EMBEDDING_URL` | Embedding 接口地址，长期记忆 demo 使用 |
| `TINYAGENT_EMBEDDING_MODEL` | Embedding 模型名称，长期记忆 demo 使用 |

当前 demo 从项目根目录的 `.env` 文件读取配置，不依赖 Spring 配置注入。

### 3. 编译和测试

```bash
./mvnw test
```

### 4. 运行不依赖数据库的 Demo

Plan-and-Execute 示例：

```bash
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.nageoffer.ai.tinyagent.react.demo.PlanAndExecuteDemo
```

Skill 技能示例：

```bash
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.nageoffer.ai.tinyagent.react.demo.SkillDemo
```

### 5. 运行数据库相关 Demo

`BitMallAgentDemo`、`LongTermMemoryDemo` 和 `ContextEngineeringDemo` 使用 PostgreSQL 持久化会话或长期记忆。运行前需要准备 PostgreSQL，并安装 pgvector 扩展。

在 `.env` 中补充数据库配置：

```properties
TINYAGENT_DB_URL=jdbc:postgresql://localhost:5432/tinyagent
TINYAGENT_DB_USER=postgres
TINYAGENT_DB_PASSWORD=postgres
```

初始化表结构：

```bash
createdb tinyagent
psql -d tinyagent -f src/main/resources/schema.sql
```

运行基础客服会话 demo：

```bash
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.nageoffer.ai.tinyagent.react.demo.BitMallAgentDemo
```

运行长期记忆 demo：

```bash
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.nageoffer.ai.tinyagent.react.demo.LongTermMemoryDemo
```

运行上下文工程 demo：

```bash
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.nageoffer.ai.tinyagent.react.demo.ContextEngineeringDemo
```

## 工作原理

### ReAct Agent

`ReActAgent` 会构造系统提示词、会话历史、长期记忆和工具列表，然后调用 `LlmClient.chatWithTools()`。当模型返回 `tool_calls` 时，Agent 通过 `ToolRegistry` 执行对应工具，并把结果作为 `tool` 消息回传给模型；当模型不再返回工具调用时，当前内容就是最终答复。

执行过程中还会处理：

- `ContextBudget`：估算并限制系统提示词、长期记忆、工具描述、历史消息和当前轮次的上下文占用。
- `ToolFilter`：根据用户问题筛选候选工具，减少一次性暴露给模型的工具数量。
- `ObservationFolder`：对过长工具结果做 JSON 摘要或截断。
- 重复调用检测：发现同参数重复调用时先提醒模型，仍重复则终止。
- 无进展检测：连续多轮推理内容高度相似时终止。

### Plan-and-Execute

`PlanAndExecuteAgent` 面向复杂任务。它先使用 `Planner` 把用户问题拆成 JSON 计划，每一步包含 `stepId`、`description` 和可选的 `toolHint`。执行器按步骤运行，每一步最多进行有限轮工具调用；如果步骤失败，会在 `maxReplanCount` 范围内根据已完成结果重新规划剩余步骤。

### Memory

项目内置多种会话记忆实现：

| 实现 | 说明 |
| --- | --- |
| `InMemoryChatMemory` | 简单内存保存，进程结束即丢失 |
| `SlidingWindowChatMemory` | 只保留最近 N 条消息 |
| `SummaryChatMemory` | 超过阈值后调用大模型压缩旧对话 |
| `HybridChatMemory` | 保留摘要 + 最近消息 |
| `JdbcChatMemory` | 把完整会话写入 PostgreSQL |
| `PersistentHybridChatMemory` | PostgreSQL 持久化 + 内存侧摘要压缩 |

长期记忆由 `PgKeyValueLongTermMemory` 和 `PgVectorLongTermMemory` 组成。会话结束后，`UserProfileExtractor` 可从对话中提取用户画像和交互记录；下一次会话开始时，`LongTermMemoryRetriever` 会把相关记忆注入上下文。

### Skill

Skill 用 Markdown 文件定义一个有作用域的执行上下文。文件由 YAML Front Matter 和 Markdown 指令正文组成，例如：

```yaml
---
name: processRefund
description: 退款处理技能
tools:
  - queryOrder
  - applyRefund
parameters:
  type: object
  properties:
    orderId:
      type: string
    reason:
      type: string
  required:
    - orderId
    - reason
---
```

`SkillRegistry` 会扫描 `src/main/resources/skills/*.md`，把每个 Skill 注册成主 Agent 可调用的工具；`SkillExecutor` 在技能内部只暴露该技能声明的工具子集。

当前内置 Skill：

| Skill | 作用 | 内部工具 |
| --- | --- | --- |
| `inquireOrderStatus` | 查询订单详情，并在已发货时继续查询物流 | `queryOrder`、`queryLogistics` |
| `processRefund` | 查询订单状态，验证退款条件并提交退款申请 | `queryOrder`、`applyRefund` |

## 内置工具

| 工具名 | 作用 | 关键参数 |
| --- | --- | --- |
| `queryOrder` | 查询订单详情 | `orderId` |
| `queryLogistics` | 查询物流轨迹 | `trackingNo` |
| `applyRefund` | 发起退款申请 | `orderId`、`reason` |
| `searchKnowledge` | 检索售后政策、常见问题或产品信息 | `query` |
| `getCurrentTime` | 获取当前时间 | 无 |

这些工具目前使用本地 Mock 数据，适合学习 Agent 调用链路。接入真实业务时，可以在工具实现中调用数据库、HTTP 服务、RPC 服务或其他内部系统。

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
        return "查询用户优惠券，返回可用优惠券列表。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "userId": {
                      "type": "string",
                      "description": "用户 ID"
                    }
                  },
                  "required": ["userId"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String userId = ToolUtils.extractRequiredField(input, "userId");
        if (userId.isBlank()) {
            return ToolUtils.missingRequiredField("userId");
        }
        return "{\"coupons\":[]}";
    }
}
```

2. 注册工具。

```java
ToolRegistry toolRegistry = new ToolRegistry();
toolRegistry.register(new QueryCouponTool());
```

3. 写清楚 `description()` 和 `parameters()`。Agent 会把它们发送给模型，描述越明确，模型越容易稳定调用。

## 扩展 Skill

在 `src/main/resources/skills` 下新增 `.md` 文件即可。建议每个 Skill 只封装一个高频业务流程，并只声明该流程需要的工具，避免技能内部上下文过宽。

## 注意事项

- 当前项目是教学和 demo 项目，内置业务工具返回的是 Mock 数据。
- `TinyagentApplication` 目前只启动 Spring Boot 应用上下文，没有暴露 HTTP 接口。
- `.env` 不应提交到代码仓库，请只提交 `.env.example`。
- 数据库相关 demo 需要先初始化 `src/main/resources/schema.sql`。
- 长期记忆和上下文工程 demo 依赖 Embedding 接口与 pgvector。

## License

Apache License 2.0
