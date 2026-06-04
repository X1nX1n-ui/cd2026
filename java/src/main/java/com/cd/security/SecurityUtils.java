package com.cd.security;

import com.cd.exception.BusinessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        return currentUser().getUserId();
    }

    public static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            throw new BusinessException("未登录");
        }
        return authenticatedUser;
    }
}
