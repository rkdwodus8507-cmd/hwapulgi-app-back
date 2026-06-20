package com.hwapulgi.api.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromotionExecuteResponse(String resultType, String error) {

    public boolean isSuccess() {
        return "SUCCESS".equals(resultType);
    }
}
