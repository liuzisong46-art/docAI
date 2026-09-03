package com.javaee.docaimcp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class McpBearerTokenFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/mcp/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MCP 请求必须携带 Bearer Token"); return; }
        McpTokenContext.set(authorization.substring(7));
        try { chain.doFilter(request, response); } finally { McpTokenContext.clear(); }
    }
}
