package com.cd.config;

import com.cd.security.JsonAccessDeniedHandler;
import com.cd.security.JsonAuthenticationEntryPoint;
import com.cd.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler jsonAccessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
                          JsonAccessDeniedHandler jsonAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jsonAuthenticationEntryPoint = jsonAuthenticationEntryPoint;
        this.jsonAccessDeniedHandler = jsonAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/login.html",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/uploads/**",
                                "/pages/**",
                                "/api/auth/login"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
