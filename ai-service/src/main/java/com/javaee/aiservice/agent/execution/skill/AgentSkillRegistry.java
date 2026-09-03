package com.javaee.aiservice.agent.execution.skill;

import com.javaee.aiservice.agent.execution.tool.AgentToolDefinition;
import com.javaee.aiservice.agent.execution.tool.AgentToolParameterDefinition;
import com.javaee.aiservice.skills.SkillDefinition;
import com.javaee.aiservice.skills.SkillRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Planner-facing catalog for composite Skills. Kept separate from atomic Agent tools. */
@Component
public class AgentSkillRegistry {

    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();

    public AgentSkillRegistry(SkillRegistry skillRegistry) {
        for (SkillDefinition definition : skillRegistry.getDefinitions()) {
            skills.put(definition.id(), definition);
        }
    }

    public boolean contains(String id) {
        return skills.containsKey(id);
    }

    public SkillDefinition get(String id) {
        return skills.get(id);
    }

    public List<SkillDefinition> list() {
        return List.copyOf(skills.values());
    }

    /** Adapter for the existing plan-step validation contract, whose field is still named toolName. */
    public AgentToolDefinition getPlannerDefinition(String id) {
        SkillDefinition definition = skills.get(id);
        if (definition == null) {
            return null;
        }
        Map<String, AgentToolParameterDefinition> schema = new LinkedHashMap<>();
        definition.parameters().forEach((name, description) -> schema.put(name,
                new AgentToolParameterDefinition(name, "string", description,
                        definition.requiredParameters().contains(name), null)));
        return new AgentToolDefinition(definition.id(), definition.description(), definition.parameters(), schema,
                definition.destructive(), "skill:" + definition.category(), definition.requiresUserAction(),
                definition.destructive() ? "high" : "low");
    }
}
