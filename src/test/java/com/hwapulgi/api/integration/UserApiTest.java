package com.hwapulgi.api.integration;

import com.hwapulgi.api.achievement.repository.AchievementRepository;
import com.hwapulgi.api.integration.support.AuthTokenFixture;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(AuthTokenFixture.class)
class UserApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private GameSessionRepository gameSessionRepository;
    @Autowired private AchievementRepository achievementRepository;
    @Autowired private AuthTokenFixture authTokens;

    private User testUser;

    @BeforeEach
    void setUp() {
        achievementRepository.deleteAll();
        gameSessionRepository.deleteAll();
        userRepository.deleteAll();
        testUser = userRepository.save(User.tossUser("1", "테스트유저"));
    }

    @Test
    void getMyProfile_returnsUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", authTokens.bearerForUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("테스트유저"));
    }

    @Test
    void getMyStats_emptyStats() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/stats")
                        .header("Authorization", authTokens.bearerForUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSessions").value(0));
    }
}
