package com.hwapulgi.api.auth.dto;

public record TossUnlinkCallbackRequest(
        Long userKey,
        String referrer
) {
}
