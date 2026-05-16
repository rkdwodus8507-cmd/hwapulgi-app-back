package com.hwapulgi.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossLoginMeResponse(
        String resultType,
        LoginMeSuccess success,
        String error
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginMeSuccess(
            Long userKey,
            String scope,
            List<String> agreedTerms,
            String name,
            String phone,
            String birthday,
            String ci,
            String di,
            String gender,
            String nationality,
            String email
    ) {
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(resultType) && success != null;
    }
}
