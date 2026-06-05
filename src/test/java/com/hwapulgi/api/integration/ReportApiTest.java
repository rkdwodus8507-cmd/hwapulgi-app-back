package com.hwapulgi.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwapulgi.api.achievement.repository.AchievementRepository;
import com.hwapulgi.api.integration.support.AuthTokenFixture;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(AuthTokenFixture.class)
class ReportApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private GameSessionRepository gameSessionRepository;
    @Autowired private AchievementRepository achievementRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private AuthTokenFixture authTokens;

    private User testUser;

    @BeforeEach
    void setUp() {
        achievementRepository.deleteAll();
        gameSessionRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        testUser = userRepository.save(User.tossUser("1", "테스트유저"));
    }

    @Test
    void getWeeklySummary_noSessions_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", authTokens.bearerForUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSessions").value(0))
                .andExpect(jsonPath("$.data.weeklyHeadline").value("이번 주 감정 기록을 시작해보세요."))
                .andExpect(jsonPath("$.data.calendarDays").isArray())
                .andExpect(jsonPath("$.data.calendarDays.length()").value(7));
    }

    @Test
    void getWeeklySummary_withSession_returnsData() throws Exception {
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사", 80, 30, 50, 5, 62, 105, null);

        String bearer = authTokens.bearerForUser(testUser);

        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSessions").value(1))
                .andExpect(jsonPath("$.data.totalHits").value(50))
                .andExpect(jsonPath("$.data.bestRelease").value(62))
                .andExpect(jsonPath("$.data.topTargets[0].label").value("상사"))
                .andExpect(jsonPath("$.data.topTargets[0].count").value(1))
                .andExpect(jsonPath("$.data.calendarDays").isArray())
                .andExpect(jsonPath("$.data.label").isString());
    }

    @Test
    void getWeeklyArchives_noSessions_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/reports/archives")
                        .header("Authorization", authTokens.bearerForUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getWeeklySummary_byWeekKey_returnsThatWeekSummary() throws Exception {
        mockMvc.perform(get("/api/v1/reports/weekly")
                        .param("weekKey", "2025-01-06")
                        .header("Authorization", authTokens.bearerForUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSessions").value(0))
                .andExpect(jsonPath("$.data.label").isString())
                .andExpect(jsonPath("$.data.calendarDays").isArray())
                .andExpect(jsonPath("$.data.calendarDays.length()").value(7));
    }

    @Test
    void getWeeklySummary_byInvalidWeekKey_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/weekly")
                        .param("weekKey", "not-a-date")
                        .header("Authorization", authTokens.bearerForUser(testUser)))
                .andExpect(status().isBadRequest());
    }
}
