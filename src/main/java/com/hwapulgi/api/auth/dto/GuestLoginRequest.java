package com.hwapulgi.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GuestLoginRequest(@NotBlank String deviceId) {
}
