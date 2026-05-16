package com.hwapulgi.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossGenerateTokenResponse(
        String resultType,
        TokenSuccess success,
        String error
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenSuccess(
            String accessToken,
            String refreshToken,
            String tokenType,
            int expiresIn,
            String scope
    ) {
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(resultType) && success != null;
    }
}
