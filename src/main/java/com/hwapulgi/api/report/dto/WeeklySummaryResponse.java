package com.hwapulgi.api.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class WeeklySummaryResponse {
    private final String label;
    private final int totalSessions;
    private final int totalHits;
    private final int totalReleased;
    private final int averageBefore;
    private final int averageAfter;
    private final int averageRelease;
    private final int bestRelease;
    private final String hardestWeekday;
    private final int streakDays;
    private final String weeklyHeadline;
    private final List<TopTarget> topTargets;
    private final List<CalendarDay> calendarDays;

    @Getter
    @AllArgsConstructor
    public static class TopTarget {
        private final String label;
        private final long count;
    }

    @Getter
    @AllArgsConstructor
    public static class CalendarDay {
        private final String dateKey;
        private final String dayLabel;
        private final int dayNumber;
        private final int angerLevel;
        private final List<SessionSummary> sessions;
    }

    @Getter
    @AllArgsConstructor
    public static class SessionSummary {
        private final String id;
        private final String target;
        private final int angerBefore;
        private final int angerAfter;
        private final int hits;
        private final int releasedPercent;
        private final String createdAt;
        private final String memo;
    }
}
