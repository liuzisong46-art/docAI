package com.javaee.aiservice.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaee.aiservice.agent.ChatService;
import com.javaee.aiservice.agent.execution.skill.AgentSkillRegistry;
import com.javaee.aiservice.agent.execution.tool.AgentToolDefinition;
import com.javaee.aiservice.agent.execution.tool.AgentToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypedSkillUpgradeTest {

    @Test
    void skillAndToolUseSeparatePlannerCatalogs() {
        SkillRegistry registry = createRegistry(mock(ChatService.class));
        AgentToolRegistry agentToolRegistry = new AgentToolRegistry();
        AgentSkillRegistry agentSkillRegistry = new AgentSkillRegistry(registry);
        AgentToolDefinition skill = agentSkillRegistry.getPlannerDefinition("html-ppt-generate");

        assertEquals(false, agentToolRegistry.contains("html-ppt-generate"));
        assertNotNull(skill);
        assertEquals(HtmlPptSkill.DEFINITION.description(), skill.getDescription());
        assertEquals("skill:" + HtmlPptSkill.DEFINITION.category(), skill.getCategory());
        assertEquals(true, skill.getParameterSchema().get("outline").isRequired());
    }

    @Test
    void registryKeepsLegacyAliasAndExecutesStructuredInput() throws Exception {
        ChatService chatService = mock(ChatService.class);
        when(chatService.callChatApiWithModelCode(anyString(), anyString()))
                .thenReturn("项目背景 | 统一定义 | 强类型参数 | 这一页介绍升级后的Skill设计以及统一执行链路，便于后续维护和扩展。");

        SkillRegistry registry = createRegistry(chatService);
        assertSame(registry.getSkill("HTML PPT Skill"), registry.getSkill("html-ppt-generate"));

        SkillExecutorService executor = new SkillExecutorService(registry, new ObjectMapper());
        Object result = executor.executeSkill("html-ppt-generate", Map.of(
                "outline", "项目背景\n技术方案",
                "title", "Skill升级",
                "theme", "tokyo-night",
                "model", "qwen3.6-plus"
        ));

        HtmlPptOutput output = assertInstanceOf(HtmlPptOutput.class, result);
        try {
            assertEquals("success", output.status());
            assertEquals("Skill升级", output.title());
            assertEquals("tokyo-night", output.theme());
            assertNotNull(output.htmlContent());
        } finally {
            Files.deleteIfExists(Path.of(output.filePath()));
        }
    }

    private SkillRegistry createRegistry(ChatService chatService) {
        return new SkillRegistry(chatService);
    }
}
