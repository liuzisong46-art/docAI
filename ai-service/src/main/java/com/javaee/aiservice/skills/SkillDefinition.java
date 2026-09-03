package com.javaee.aiservice.skills;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of metadata used both by the Agent planner and the Skill executor.
 */
public record SkillDefinition(
        String id,
        String legacyName,
        String description,
        Map<String, String> parameters,
        Set<String> requiredParameters,
        boolean destructive,
        String category,
        boolean requiresUserAction
) {
    public SkillDefinition {
        parameters = parameters == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(parameters));
        requiredParameters = requiredParameters == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(requiredParameters));
    }
}
