package com.workflow.workflowplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Intercepta OPTIONS antes que Spring Security y responde 200 inmediatamente.
 * Esto resuelve el preflight fallando por "Required request body is missing"
 * cuando el OPTIONS llega al controller antes de ser detenido por el filtro CORS.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PreflightCorsFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_ORIGINS = List.of(
        "http://localhost:4200",
        "https://main.d3rs2veleasrg5.amplifyapp.com",
        "https://workflow-demo.site",
        "https://www.workflow-demo.site",
        "https://api.workflow-demo.site"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");

        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers",
                    "Authorization, Content-Type, X-Requested-With, Accept, Origin");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        // OPTIONS preflight → responder 200 inmediatamente sin pasar a Spring Security
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(request, response);
    }
}
