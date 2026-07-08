package com.nageoffer.ai.tinyagent.react.skill;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能定义：从 YAML 文件加载的数据类
 */
@Data
@NoArgsConstructor
public class Skill {

    private String name;
    private String description;
    private List<String> tools;
    private String instructions;
}
