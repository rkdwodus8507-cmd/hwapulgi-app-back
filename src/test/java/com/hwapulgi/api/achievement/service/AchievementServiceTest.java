package com.hwapulgi.api.achievement.service;

import com.hwapulgi.api.achievement.entity.Achievement;
import com.hwapulgi.api.achievement.entity.AchievementType;
import com.hwapulgi.api.achievement.dto.AchievementResponse;
import com.hwapulgi.api.achievement.repository.AchievementRepository;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock private AchievementRepository achievementRepository;
    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private UserService userService;
    @InjectMocks private AchievementService achievementService;

    @Test
    void checkAndAward_hitsThreshold_awardsAchievement() {
        User user = new User("1", "테스트");
        given(userService.findById(1L)).willReturn(user);

        GameSession session = GameSession.builder()
                .user(user).target("회사").targetNickname("상사")
                .angerBefore(100).angerAfter(10).hits(150).skillShots(0)
                .releasedPercent(90).points(200).build();

        given(achievementRepository.findByUserIdOrderByAchievedAtDesc(any())).willReturn(Collections.emptyList());
        given(gameSessionRepository.sumHitsByUserId(any())).willReturn(150);
        given(gameSessionRepository.countByUserId(any())).willReturn(1L);
        given(achievementRepository.save(any(Achievement.class)))
                .willAnswer(inv -> inv.getArgument(0));

        List<AchievementResponse> results = achievementService.checkAndAward(1L, session, 0);

        assertThat(results).isNotEmpty();
        assertThat(results.stream().map(AchievementResponse::getType))
                .contains("HITS_100", "RELEASE_90", "RELEASE_80");
    }

    @Test
    void checkAndAward_alreadyHas_skips() {
        User user = new User("1", "테스트");
        given(userService.findById(1L)).willReturn(user);

        GameSession session = GameSession.builder()
                .user(user).target("회사").targetNickname("상사")
                .angerBefore(100).angerAfter(50).hits(150).skillShots(0)
                .releasedPercent(50).points(100).build();

        // 모든 업적을 이미 보유
        Achievement existing = new Achievement(user, AchievementType.HITS_100);
        given(achievementRepository.findByUserIdOrderByAchievedAtDesc(any()))
                .willReturn(List.of(existing));
        given(gameSessionRepository.sumHitsByUserId(any())).willReturn(150);
        given(gameSessionRepository.countByUserId(any())).willReturn(1L);

        List<AchievementResponse> results = achievementService.checkAndAward(1L, session, 0);

        // HITS_100은 이미 보유하므로 스킵, 나머지 조건 미달성
        assertThat(results.stream().map(AchievementResponse::getType))
                .doesNotContain("HITS_100");
    }
}
