package com.nageoffer.ai.tinyagent.react.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 技能注册表：从目录扫描 SKILL.md 文件（YAML 前置元数据 + Markdown 指令正文）
 */
public class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public SkillRegistry() {
    }

    /**
     * 从指定目录扫描所有 .md 文件，按 SKILL.md 格式解析（YAML 前置元数据 + Markdown 正文）
     */
    public void loadFromDirectory(Path skillsDir) {
        if (!Files.isDirectory(skillsDir)) {
            System.out.println("[SkillRegistry] 技能目录不存在：" + skillsDir);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir, "*.md")) {
            for (Path file : stream) {
                try {
                    Skill skill = parseSkillMd(file);
                    skills.put(skill.getName(), skill);
                    System.out.println("[SkillRegistry] 加载技能：" + skill.getName()
                            + "（" + file.getFileName() + "）");
                } catch (Exception e) {
                    System.out.println("[SkillRegistry] 解析失败：" + file.getFileName()
                            + " → " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("[SkillRegistry] 扫描目录失败：" + e.getMessage());
        }
    }

    /**
     * 解析 SKILL.md：提取 YAML 前置元数据和 Markdown 正文
     */
    private Skill parseSkillMd(Path file) throws IOException {
        String content = Files.readString(file);
        String trimmed = content.strip();

        if (!trimmed.startsWith("---")) {
            throw new IllegalArgumentException("缺少 YAML 前置元数据（文件应以 --- 开头）");
        }

        int secondSep = trimmed.indexOf("---", 3);
        if (secondSep < 0) {
            throw new IllegalArgumentException("YAML 前置元数据未闭合（缺少第二个 ---）");
        }

        String yamlPart = trimmed.substring(3, secondSep).strip();
        String markdownBody = trimmed.substring(secondSep + 3).strip();

        Skill skill = yamlMapper.readValue(yamlPart, Skill.class);
        skill.setInstructions(markdownBody);
        return skill;
    }

    /**
     * 构建主 Agent 的工具注册表：
     * - 收集所有技能声明的 tools，标记为"技能专属"
     * - 只注册不属于任何技能的通用工具 + activate_skill
     * - 技能专属工具在 activate_skill 激活后由 DynamicToolProvider 动态注入
     */
    public ToolRegistry buildAgentRegistry(ToolRegistry baseRegistry) {
        Set<String> skillScopedNames = new HashSet<>();
        for (Skill skill : skills.values()) {
            if (skill.getTools() != null) {
                skillScopedNames.addAll(skill.getTools());
            }
        }

        ToolRegistry agentRegistry = new ToolRegistry();
        for (Tool tool : baseRegistry.getTools()) {
            if (!skillScopedNames.contains(tool.name())) {
                agentRegistry.register(tool);
            }
        }
        agentRegistry.register(new ActivateSkillTool(this, baseRegistry));

        System.out.println("[SkillRegistry] 技能专属工具（激活前不可见）：" + skillScopedNames);

        return agentRegistry;
    }

    public Skill getSkill(String name) {
        return skills.get(name);
    }

    public Collection<Skill> getSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }
}
