package com.hwapulgi.api.session.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GameSessionCreateRequest {

    @NotBlank
    private String target;

    private String customTarget;

    @NotBlank
    private String targetNickname;

    @Min(0) @Max(100)
    private int angerBefore;

    @Min(0) @Max(100)
    private int angerAfter;

    @Min(0)
    private int hits;

    @Min(0)
    private int skillShots;

    @Min(0) @Max(100)
    private int releasedPercent;

    @Min(0)
    private int points;

    private String memo;
}
