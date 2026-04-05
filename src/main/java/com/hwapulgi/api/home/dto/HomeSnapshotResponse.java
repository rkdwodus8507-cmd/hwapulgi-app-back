package com.hwapulgi.api.home.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HomeSnapshotResponse {
    private final int todayCount;
    private final int latestReleasePercent;
    private final String latestTarget;
    private final String primaryTarget;
    private final int weeklySessions;
    private final int weeklyAverageRelease;
}
