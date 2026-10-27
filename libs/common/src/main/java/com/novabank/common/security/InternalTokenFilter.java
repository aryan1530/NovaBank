package com.novabank.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {
    @Value("${novabank.internal-token:novabank-internal-dev-token}")
    private String expected;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/internal/")) {
            String token = request.getHeader("X-Internal-Token");
            if (token == null || !token.equals(expected)) {
                response.setStatus(401);
                response.getWriter().write("{\"code\":\"INTERNAL_AUTH\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
