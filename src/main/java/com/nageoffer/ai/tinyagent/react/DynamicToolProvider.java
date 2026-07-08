package com.nageoffer.ai.tinyagent.react;

import java.util.List;

/**
 * 动态工具提供者：工具执行后可以向 Agent 注入新的工具
 * <p>
 * 典型场景：activate_skill 激活技能后，将技能专属工具加入 LLM 可见列表
 */
public interface DynamicToolProvider {

    List<Tool> dynamicTools();
}
