package com.javaee.aiservice.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SkillExecutorService {

    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    @Autowired
    public SkillExecutorService(SkillRegistry skillRegistry, ObjectMapper objectMapper) {
        this.skillRegistry = skillRegistry;
        this.objectMapper = objectMapper;
    }

    public Object executeSkill(String skillName, Object... parameters) {
        Skill skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在: " + skillName);
        }
        return skill.execute(parameters);
    }

    public Object executeSkill(String skillId, Map<String, Object> parameters) {
        Skill skill = skillRegistry.getSkill(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在: " + skillId);
        }
        if (!(skill instanceof TypedSkill<?, ?> typedSkill)) {
            throw new IllegalArgumentException("技能不支持结构化参数: " + skillId);
        }

        validateRequiredParameters(typedSkill.getDefinition(), parameters);
        return executeTyped(typedSkill, parameters);
    }

    private void validateRequiredParameters(SkillDefinition definition, Map<String, Object> parameters) {
        Map<String, Object> safeParameters = parameters == null ? Map.of() : parameters;
        for (String required : definition.requiredParameters()) {
            Object value = safeParameters.get(required);
            if (value == null || value instanceof String text && text.isBlank()) {
                throw new IllegalArgumentException("技能参数不能为空: " + required);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <I, O> O executeTyped(TypedSkill<I, O> skill, Map<String, Object> parameters) {
        I input = objectMapper.convertValue(parameters == null ? Map.of() : parameters, skill.getInputType());
        return skill.executeTyped(input);
    }

    public String getSkillDescription(String skillName) {
        Skill skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在: " + skillName);
        }
        return skill.getDescription();
    }
}
