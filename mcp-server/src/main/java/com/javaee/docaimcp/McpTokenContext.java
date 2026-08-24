package com.javaee.docaimcp;
final class McpTokenContext {
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();
    static void set(String token) { TOKEN.set(token); }
    static String getRequired() { String token = TOKEN.get(); if (token == null || token.isBlank()) throw new IllegalStateException("MCP 调用缺少用户 Token"); return token; }
    static void clear() { TOKEN.remove(); }
}
