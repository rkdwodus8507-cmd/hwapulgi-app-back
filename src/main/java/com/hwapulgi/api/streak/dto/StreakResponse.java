package com.hwapulgi.api.streak.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StreakResponse {
    private final int currentStreak;
    private final int bestStreak;
    private final int totalPlayDays;
}
