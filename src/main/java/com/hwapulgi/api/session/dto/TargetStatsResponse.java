package com.hwapulgi.api.session.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TargetStatsResponse {
    private final String target;
    private final long sessionCount;
    private final long totalHits;
    private final double avgRelease;
}
