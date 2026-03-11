package com.example.demo.auth;

import com.example.demo.auth.dto.AuthTokenResponse;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.RefreshRequest;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.common.exception.AppException;
import com.example.demo.domain.entity.AppUser;
import com.example.demo.domain.repository.AppUserRepository;
import com.example.demo.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        if (appUserRepository.findByEmail(email).isPresent()) {
            throw new AppException(HttpStatus.CONFLICT, 4091, "EMAIL_ALREADY_EXISTS");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setDisplayName(req.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus((byte) 1);
        appUserRepository.save(user);
        return buildToken(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        AppUser user = appUserRepository.findByEmail(email)
            .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, 4011, "INVALID_EMAIL_OR_PASSWORD"));
        if (user.getStatus() == null || user.getStatus() != 1 || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, 4011, "INVALID_EMAIL_OR_PASSWORD");
        }
        return buildToken(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtTokenService.parse(req.refreshToken());
        } catch (JwtException ex) {
            throw new AppException(HttpStatus.UNAUTHORIZED, 4012, "INVALID_REFRESH_TOKEN");
        }
        String typ = claims.get("typ", String.class);
        if (!"refresh".equals(typ)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, 4012, "INVALID_REFRESH_TOKEN");
        }
        Long userId = Long.parseLong(claims.getSubject());
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, 4012, "INVALID_REFRESH_TOKEN"));
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new AppException(HttpStatus.UNAUTHORIZED, 4012, "INVALID_REFRESH_TOKEN");
        }
        return buildToken(user);
    }

    private AuthTokenResponse buildToken(AppUser user) {
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenService.createRefreshToken(user.getId(), user.getEmail());
        return new AuthTokenResponse(accessToken, refreshToken, jwtTokenService.accessExpireSeconds());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
