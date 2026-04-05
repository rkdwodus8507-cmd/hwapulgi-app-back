package com.hwapulgi.api.integration;

import com.hwapulgi.api.achievement.repository.AchievementRepository;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StreakApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AchievementRepository achievementRepository;
    @Autowired private GameSessionRepository gameSessionRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        achievementRepository.deleteAll();
        gameSessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getMyStreak_noSessions() throws Exception {
        mockMvc.perform(get("/api/v1/streaks/me")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreak").value(0))
                .andExpect(jsonPath("$.data.bestStreak").value(0))
                .andExpect(jsonPath("$.data.totalPlayDays").value(0));
    }
}
