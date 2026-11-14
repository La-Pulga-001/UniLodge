package com.unilodge.web.security;

import com.unilodge.common.enums.UserRole;
import com.unilodge.common.enums.UserStatus;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.UserRepository;
import com.unilodge.service.security.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the bearer token on each request and re-loads the account so a
 * suspension takes effect immediately, even for an already issued token.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            try {
                UUID userId = tokenService.parseAccessToken(token);
                Optional<User> user = userRepository.findById(userId);
                if (user.isPresent() && user.get().getStatus() != UserStatus.SUSPENDED) {
                    User account = user.get();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(account, null, authoritiesFor(account));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                // Invalid, expired or malformed tokens simply fall through as anonymous.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private static List<GrantedAuthority> authoritiesFor(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (user.getRole() == UserRole.ADMIN) {
            authorities.add(new SimpleGrantedAuthority(
                    user.isAdminVerified() ? "ROLE_ADMIN" : "ROLE_PENDING_ADMIN"));
        } else if (user.getRole() == UserRole.LANDLORD) {
            authorities.add(new SimpleGrantedAuthority(switch (user.getStatus()) {
                case ACTIVE -> "ROLE_LANDLORD";
                case PENDING_APPROVAL -> "ROLE_LANDLORD_PENDING";
                case REJECTED -> "ROLE_LANDLORD_REJECTED";
                default -> "ROLE_LANDLORD_SUSPENDED";
            }));
        } else if (user.getRole() == UserRole.STUDENT) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STUDENT"));
        }
        return authorities;
    }
}
