package com.javaee.docaimcp;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class DocAiMcpTools {
    private final RestClient client;
    public DocAiMcpTools(RestClient.Builder builder, @Value("${docai.ai-base-url}") String baseUrl) { this.client = builder.baseUrl(baseUrl).build(); }
    @Tool(description = "检索当前用户在 DocAI 中有权限访问的知识库，使用向量、BM25 融合和重排序。")
    public String searchKnowledge(String query, Integer topK, String knowledgeBaseId) {
        String uri =
                UriComponentsBuilder.fromPath("/api/ai/rag/search/hybrid/rerank")
                        .queryParam("query", query)
                        .queryParam("topK", topK == null ? 5 : Math.max(1, Math.min(topK, 20)))
                        .queryParam("knowledgeBaseId", knowledgeBaseId == null || knowledgeBaseId.isBlank() ? "default" : knowledgeBaseId)
                        .queryParam("strategy", "HYBRID").build()
                        .toUriString(); return get(uri);
    }
    @Tool(description = "列出当前用户在 DocAI 知识库中有权限访问的文档 ID。")
    public String listDocuments() { return get("/api/ai/rag/documents"); }
    @Tool(description = "查询当前用户有权限访问的 DocAI 文档元数据。")
    public String getDocumentMetadata(String documentId) { return get("/api/ai/rag/document/" + documentId + "/metadata"); }
    @Tool(description = "获取 DocAI Agent 可调用的 Skills 和工具目录，仅返回定义，不执行写入或删除操作。")
    public String listAgentTools() { return get("/api/ai/agent/tools"); }
    private String get(String uri) {
        return client
                .get()
                .uri(uri)
                .headers(h -> h.setBearerAuth(McpTokenContext.getRequired()))
                .retrieve()
                .body(String.class); }
}
