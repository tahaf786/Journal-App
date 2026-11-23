package com.devtaha.journalapp.auth;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
}
