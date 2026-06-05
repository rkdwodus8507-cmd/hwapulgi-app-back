package com.hwapulgi.api.auth.dto;

public record GuestLoginResponse(
        Long userId,
        String nickname,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
