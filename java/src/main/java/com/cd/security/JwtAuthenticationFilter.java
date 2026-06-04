package com.cd.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final SecurityUserDetailsService securityUserDetailsService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   SecurityUserDetailsService securityUserDetailsService) {
        this.jwtTokenService = jwtTokenService;
        this.securityUserDetailsService = securityUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            if (jwtTokenService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    Long userId = jwtTokenService.getUserId(token);
                    AuthenticatedUser authenticatedUser = securityUserDetailsService.loadUserById(userId);
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            authenticatedUser,
                            null,
                            authenticatedUser.getAuthorities()
                    );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } catch (RuntimeException exception) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
