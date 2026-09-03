package com.javaee.aiservice.skills.tool;

/**
 * Atomic operation used inside a Skill workflow.
 * A tool performs one bounded action and does not decide the overall workflow.
 */
public interface SkillTool<I, O> {

    String id();

    O execute(I input);
}
