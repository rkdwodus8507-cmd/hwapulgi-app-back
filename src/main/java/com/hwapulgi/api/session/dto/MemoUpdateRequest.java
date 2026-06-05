package com.hwapulgi.api.session.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemoUpdateRequest {
    @Size(max = 1000)
    private String memo;
}
