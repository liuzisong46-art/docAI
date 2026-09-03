package com.javaee.aiservice.skills;

import com.javaee.aiservice.agent.ChatService;
import com.javaee.aiservice.skills.tool.HtmlPresentationRenderTool;
import com.javaee.aiservice.skills.tool.LocalArtifactWriteTool;
import com.javaee.aiservice.skills.tool.PptContentGenerationTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Map<String, TypedSkill<?, ?>> typedSkills = new LinkedHashMap<>();

    @Autowired
    public SkillRegistry(ChatService chatService) {
        registerSkill(new HtmlPptSkill(
                new PptContentGenerationTool(chatService),
                new HtmlPresentationRenderTool(),
                new LocalArtifactWriteTool()));
    }

    public void registerSkill(Skill skill) {
        skills.put(skill.getName(), skill);
        if (skill instanceof TypedSkill<?, ?> typedSkill) {
            String id = typedSkill.getDefinition().id();
            TypedSkill<?, ?> existing = typedSkills.putIfAbsent(id, typedSkill);
            if (existing != null && existing != typedSkill) {
                throw new IllegalStateException("重复的Skill ID: " + id);
            }
            skills.put(id, skill);
        }
    }

    public Skill getSkill(String name) {
        return skills.get(name);
    }

    public Map<String, Skill> getAllSkills() {
        return Collections.unmodifiableMap(skills);
    }

    public List<SkillDefinition> getDefinitions() {
        return typedSkills.values().stream()
                .map(TypedSkill::getDefinition)
                .toList();
    }
}
