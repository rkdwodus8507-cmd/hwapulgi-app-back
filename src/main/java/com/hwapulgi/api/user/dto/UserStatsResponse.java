package com.hwapulgi.api.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserStatsResponse {
    private final int totalSessions;
    private final int totalHits;
    private final int totalPoints;
    private final int bestRelease;
    private final int avgRelease;
}
