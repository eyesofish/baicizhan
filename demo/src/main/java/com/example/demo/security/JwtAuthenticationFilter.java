package com.example.demo.security;

import com.example.demo.domain.entity.AppUser;
import com.example.demo.domain.repository.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;
    private final AppUserRepository appUserRepository;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, AppUserRepository appUserRepository) {
        this.jwtTokenService = jwtTokenService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtTokenService.parse(token);
                if ("access".equals(claims.get("typ", String.class))) {
                    Long userId = Long.parseLong(claims.getSubject());
                    Optional<AppUser> userOpt = appUserRepository.findById(userId);
                    if (userOpt.isPresent() && userOpt.get().getStatus() != null && userOpt.get().getStatus() == 1) {
                        AppUser user = userOpt.get();
                        UserPrincipal principal =
                            new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), true);
                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Let Spring Security handle unauthorized access later.
            }
        }
        filterChain.doFilter(request, response);
    }
}
