package com.unilodge.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unilodge.common.dto.ApiError;
import com.unilodge.service.config.AppProperties;
import com.unilodge.web.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/** Stateless, token-based security for the API and the static web client. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,
                          AppProperties properties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Static web client
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/error",
                                "/*.html", "/css/**", "/js/**", "/images/**", "/assets/**", "/uploads/**")
                        .permitAll()
                        // API documentation
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Open authentication flows
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/password/forgot",
                                "/api/auth/password/verify",
                                "/api/auth/password/reset",
                                "/api/auth/setup/request-code",
                                "/api/auth/setup/complete").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/invitations/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/invitations/**").permitAll()
                        // Personal collections must be checked before the public GET patterns
                        .requestMatchers(HttpMethod.GET,
                                "/api/properties/mine",
                                "/api/properties/saved",
                                "/api/properties/saved/ids").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/posts/saved",
                                "/api/posts/saved/ids").authenticated()
                        // Public catalogue
                        .requestMatchers(HttpMethod.GET,
                                "/api/universities",
                                "/api/properties",
                                "/api/properties/*",
                                "/api/posts",
                                "/api/posts/*",
                                "/api/posts/*/comments",
                                "/api/files/**",
                                "/api/geo/**").permitAll()
                        // Administration
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED", "Please sign in to continue."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN", "You do not have permission to do that.")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(status, code, message));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> configured = properties.getCors().getAllowedOrigins();
        if (configured == null || configured.isEmpty()) {
            configuration.setAllowedOriginPatterns(List.of(
                    "http://localhost:*", "http://127.0.0.1:*"));
        } else {
            configuration.setAllowedOrigins(configured);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
