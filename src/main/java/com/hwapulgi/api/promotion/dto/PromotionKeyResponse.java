package com.hwapulgi.api.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromotionKeyResponse(String resultType, Success success, String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Success(String key) {
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(resultType) && success != null && success.key() != null;
    }
}
