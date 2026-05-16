package com.hwapulgi.api.auth.dto;

public record TossGenerateTokenRequest(
        String authorizationCode,
        String referrer
) {
}
