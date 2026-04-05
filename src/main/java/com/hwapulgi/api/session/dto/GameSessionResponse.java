package com.hwapulgi.api.session.dto;

import com.hwapulgi.api.session.entity.GameSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class GameSessionResponse {
    private final UUID id;
    private final String target;
    private final String customTarget;
    private final String targetNickname;
    private final int angerBefore;
    private final int angerAfter;
    private final int hits;
    private final int skillShots;
    private final int releasedPercent;
    private final int points;
    private final String memo;
    private final LocalDateTime createdAt;

    public static GameSessionResponse from(GameSession session) {
        return new GameSessionResponse(
                session.getId(), session.getTarget(), session.getCustomTarget(),
                session.getTargetNickname(), session.getAngerBefore(), session.getAngerAfter(),
                session.getHits(), session.getSkillShots(), session.getReleasedPercent(),
                session.getPoints(), session.getMemo(), session.getCreatedAt()
        );
    }
}
