package com.hwapulgi.api.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class WeeklyArchiveResponse {
    private final String id;
    private final String label;
    private final String periodText;
    private final int totalSessions;
    private final int totalHits;
    private final int totalReleased;
    private final int averageBefore;
    private final int averageAfter;
    private final int averageRelease;
    private final String topTarget;
    private final String hardestWeekday;
}
