package com.example.demo.auth.dto;

public record AuthTokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
