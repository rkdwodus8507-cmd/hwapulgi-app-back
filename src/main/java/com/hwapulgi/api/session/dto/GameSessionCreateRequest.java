package com.hwapulgi.api.session.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GameSessionCreateRequest {

    @NotBlank @Size(max = 20)
    private String target;

    @Size(max = 50)
    private String customTarget;

    @NotBlank @Size(max = 50)
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

    @Size(max = 1000)
    private String memo;
}
