package com.hwapulgi.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TossLoginRequest(
        @NotBlank String authorizationCode,
        @NotBlank String referrer
) {
}
