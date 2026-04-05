package com.hwapulgi.api.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AngerAfterUpdateRequest {
    @Min(0) @Max(100)
    private int angerAfter;
}
