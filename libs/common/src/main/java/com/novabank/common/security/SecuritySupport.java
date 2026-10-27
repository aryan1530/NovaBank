package com.novabank.common.security;

import com.novabank.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecuritySupport {
    private SecuritySupport() {}

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Login required");
        }
        return id;
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public static void requireSelfOrAdmin(Long customerUserId) {
        if (hasRole("ADMIN") || hasRole("FRAUD_ANALYST")) {
            return;
        }
        if (!currentUserId().equals(customerUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ISOLATION", "Cannot access another customer");
        }
    }
}
