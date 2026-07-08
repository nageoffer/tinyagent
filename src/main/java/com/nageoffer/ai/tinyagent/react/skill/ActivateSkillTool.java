package com.nageoffer.ai.tinyagent.react.skill;

import com.nageoffer.ai.tinyagent.react.DynamicToolProvider;
import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 技能激活工具：LLM 调用 activate_skill(name) 加载技能指令到主上下文，
 * 同时通过 DynamicToolProvider 将技能专属工具注入 LLM 可见列表
 */
public class ActivateSkillTool implements Tool, DynamicToolProvider {

    private final SkillRegistry skillRegistry;
    private final ToolRegistry executionRegistry;
    private final String parametersJson;
    private final String descriptionText;
    private final List<Tool> activatedTools = new ArrayList<>();

    public ActivateSkillTool(SkillRegistry skillRegistry, ToolRegistry executionRegistry) {
        this.skillRegistry = skillRegistry;
        this.executionRegistry = executionRegistry;
        this.parametersJson = buildParametersJson(skillRegistry.getSkills());
        this.descriptionText = buildDescription(skillRegistry.getSkills());
    }

    @Override
    public String name() {
        return "activate_skill";
    }

    @Override
    public String description() {
        return descriptionText;
    }

    @Override
    public String parameters() {
        return parametersJson;
    }

    @Override
    public String invoke(String input) {
        String skillName = ToolUtils.extractRequiredField(input, "name");
        if (skillName.isBlank()) {
            return "{\"error\":\"缺少必填参数 name\"}";
        }

        Skill skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            return "{\"error\":\"未找到技能：" + skillName + "\"}";
        }

        activatedTools.clear();
        if (skill.getTools() != null) {
            for (String toolName : skill.getTools()) {
                Tool tool = executionRegistry.getTool(toolName);
                if (tool != null) {
                    activatedTools.add(tool);
                }
            }
        }

        System.out.println("[Skill] 激活技能：" + skillName
                + "，解锁工具：" + skill.getTools());

        return "技能已激活：" + skillName
                + "。请严格按照以下指令操作：\n\n"
                + skill.getInstructions();
    }

    @Override
    public List<Tool> dynamicTools() {
        List<Tool> result = new ArrayList<>(activatedTools);
        activatedTools.clear();
        return result;
    }

    private static String buildParametersJson(Collection<Skill> skills) {
        String enumValues = skills.stream()
                .map(s -> "\"" + s.getName() + "\"")
                .collect(Collectors.joining(","));
        return "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\","
                + "\"description\":\"要激活的技能名称\",\"enum\":["
                + enumValues + "]}},\"required\":[\"name\"]}";
    }

    private static String buildDescription(Collection<Skill> skills) {
        StringBuilder sb = new StringBuilder("激活一个技能来获取专业处理指令。可用技能：");
        boolean first = true;
        for (Skill skill : skills) {
            if (!first) {
                sb.append("、");
            }
            sb.append(skill.getName()).append("（").append(skill.getDescription().strip()).append("）");
            first = false;
        }
        sb.append("。激活后会返回详细的处理步骤，并解锁该技能所需的专属工具。");
        return sb.toString();
    }
}
