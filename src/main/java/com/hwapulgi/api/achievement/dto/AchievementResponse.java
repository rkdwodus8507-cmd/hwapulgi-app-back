package com.hwapulgi.api.achievement.dto;

import com.hwapulgi.api.achievement.entity.Achievement;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AchievementResponse {
    private final String type;
    private final String title;
    private final String description;
    private final LocalDateTime achievedAt;

    public static AchievementResponse from(Achievement achievement) {
        return new AchievementResponse(
                achievement.getAchievementType().name(),
                achievement.getAchievementType().getTitle(),
                achievement.getAchievementType().getDescription(),
                achievement.getAchievedAt()
        );
    }
}
