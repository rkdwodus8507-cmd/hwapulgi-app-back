package com.hwapulgi.api.achievement.service;

import com.hwapulgi.api.achievement.dto.AchievementResponse;
import com.hwapulgi.api.achievement.entity.Achievement;
import com.hwapulgi.api.achievement.entity.AchievementType;
import com.hwapulgi.api.achievement.repository.AchievementRepository;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final GameSessionRepository gameSessionRepository;
    private final UserService userService;

    public List<AchievementResponse> getMyAchievements(Long userId) {
        return achievementRepository.findByUserIdOrderByAchievedAtDesc(userId).stream()
                .map(AchievementResponse::from)
                .toList();
    }

    @Transactional
    public List<AchievementResponse> checkAndAward(Long userId, GameSession latestSession, int currentStreak) {
        User user = userService.findById(userId);

        // 배치 조회: 이미 보유한 업적 타입 세트
        Set<AchievementType> existing = achievementRepository.findByUserIdOrderByAchievedAtDesc(userId).stream()
                .map(Achievement::getAchievementType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AchievementType.class)));

        // 집계 쿼리로 통계 조회 (전체 세션 로드 X)
        int totalHits = gameSessionRepository.sumHitsByUserId(userId);
        long totalSessions = gameSessionRepository.countByUserId(userId);

        List<AchievementResponse> newAchievements = new ArrayList<>();

        // 타격 수
        tryAward(user, AchievementType.HITS_100, totalHits >= 100, existing, newAchievements);
        tryAward(user, AchievementType.HITS_500, totalHits >= 500, existing, newAchievements);
        tryAward(user, AchievementType.HITS_1000, totalHits >= 1000, existing, newAchievements);

        // 세션 수
        tryAward(user, AchievementType.SESSIONS_10, totalSessions >= 10, existing, newAchievements);
        tryAward(user, AchievementType.SESSIONS_50, totalSessions >= 50, existing, newAchievements);
        tryAward(user, AchievementType.SESSIONS_100, totalSessions >= 100, existing, newAchievements);

        // 해소율 + 포인트 (최신 세션 기준)
        if (latestSession != null) {
            int release = latestSession.getReleasedPercent();
            tryAward(user, AchievementType.RELEASE_80, release >= 80, existing, newAchievements);
            tryAward(user, AchievementType.RELEASE_90, release >= 90, existing, newAchievements);
            tryAward(user, AchievementType.RELEASE_100, release >= 100, existing, newAchievements);

            int points = latestSession.getPoints();
            tryAward(user, AchievementType.POINTS_500, points >= 500, existing, newAchievements);
            tryAward(user, AchievementType.POINTS_1000, points >= 1000, existing, newAchievements);
        }

        // 스트릭
        tryAward(user, AchievementType.STREAK_3, currentStreak >= 3, existing, newAchievements);
        tryAward(user, AchievementType.STREAK_7, currentStreak >= 7, existing, newAchievements);
        tryAward(user, AchievementType.STREAK_30, currentStreak >= 30, existing, newAchievements);

        return newAchievements;
    }

    private void tryAward(User user, AchievementType type, boolean condition,
                          Set<AchievementType> existing, List<AchievementResponse> newAchievements) {
        if (condition && !existing.contains(type)) {
            Achievement achievement = achievementRepository.save(new Achievement(user, type));
            existing.add(type);
            newAchievements.add(AchievementResponse.from(achievement));
            log.info("Achievement unlocked: userId={}, type={}", user.getId(), type);
        }
    }
}
