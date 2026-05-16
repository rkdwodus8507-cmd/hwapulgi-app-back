package com.hwapulgi.api.auth.dto;

public record TossLoginResponse(
        Long userId,
        String nickname,
        String accessToken,
        String refreshToken,
        int expiresIn
) {
}
