package com.javaee.docaimcp;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration public class McpToolConfig { @Bean ToolCallbackProvider docAiTools(DocAiMcpTools tools) { return MethodToolCallbackProvider.builder().toolObjects(tools).build(); } }
