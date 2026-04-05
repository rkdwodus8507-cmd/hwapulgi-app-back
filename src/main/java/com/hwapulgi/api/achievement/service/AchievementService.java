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
import java.util.List;

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
    public List<AchievementResponse> checkAndAward(Long userId, int currentStreak) {
        User user = userService.findById(userId);
        List<GameSession> allSessions = gameSessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), null).getContent();

        int totalHits = allSessions.stream().mapToInt(GameSession::getHits).sum();
        int totalSessions = allSessions.size();
        GameSession latest = allSessions.isEmpty() ? null : allSessions.get(0);

        List<AchievementResponse> newAchievements = new ArrayList<>();

        checkAndAward(user, AchievementType.HITS_100, totalHits >= 100, newAchievements);
        checkAndAward(user, AchievementType.HITS_500, totalHits >= 500, newAchievements);
        checkAndAward(user, AchievementType.HITS_1000, totalHits >= 1000, newAchievements);

        checkAndAward(user, AchievementType.SESSIONS_10, totalSessions >= 10, newAchievements);
        checkAndAward(user, AchievementType.SESSIONS_50, totalSessions >= 50, newAchievements);
        checkAndAward(user, AchievementType.SESSIONS_100, totalSessions >= 100, newAchievements);

        if (latest != null) {
            int release = latest.getReleasedPercent();
            checkAndAward(user, AchievementType.RELEASE_80, release >= 80, newAchievements);
            checkAndAward(user, AchievementType.RELEASE_90, release >= 90, newAchievements);
            checkAndAward(user, AchievementType.RELEASE_100, release >= 100, newAchievements);

            int points = latest.getPoints();
            checkAndAward(user, AchievementType.POINTS_500, points >= 500, newAchievements);
            checkAndAward(user, AchievementType.POINTS_1000, points >= 1000, newAchievements);
        }

        checkAndAward(user, AchievementType.STREAK_3, currentStreak >= 3, newAchievements);
        checkAndAward(user, AchievementType.STREAK_7, currentStreak >= 7, newAchievements);
        checkAndAward(user, AchievementType.STREAK_30, currentStreak >= 30, newAchievements);

        return newAchievements;
    }

    private void checkAndAward(User user, AchievementType type, boolean condition,
                               List<AchievementResponse> newAchievements) {
        if (condition && !achievementRepository.existsByUserIdAndAchievementType(user.getId(), type)) {
            Achievement achievement = achievementRepository.save(new Achievement(user, type));
            newAchievements.add(AchievementResponse.from(achievement));
            log.info("Achievement unlocked: userId={}, type={}", user.getId(), type);
        }
    }
}
