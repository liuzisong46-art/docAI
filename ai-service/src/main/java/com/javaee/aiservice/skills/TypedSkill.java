package com.javaee.aiservice.skills;

/**
 * Strongly typed Skill contract used by Agent execution.
 */
public interface TypedSkill<I, O> extends Skill {

    SkillDefinition getDefinition();

    Class<I> getInputType();

    O executeTyped(I input);
}
