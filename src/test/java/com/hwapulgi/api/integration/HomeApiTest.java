package com.hwapulgi.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwapulgi.api.achievement.repository.AchievementRepository;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class HomeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private AchievementRepository achievementRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        achievementRepository.deleteAll();
        gameSessionRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        userRepository.save(new User("1", "테스트유저"));
    }

    @Test
    void getSnapshot_noSessions_returnsDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/home/snapshot")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayCount").value(0))
                .andExpect(jsonPath("$.data.latestReleasePercent").value(0))
                .andExpect(jsonPath("$.data.latestTarget").value("-"))
                .andExpect(jsonPath("$.data.primaryTarget").value("-"))
                .andExpect(jsonPath("$.data.weeklySessions").value(0))
                .andExpect(jsonPath("$.data.weeklyAverageRelease").value(0));
    }

    @Test
    void getSnapshot_withSession_returnsCorrectData() throws Exception {
        // points = 10 + 50 + (5*4) + (80-30)/2 = 105
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사", 80, 30, 50, 5, 62, 105, "테스트 메모");

        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "1:테스트유저")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/home/snapshot")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayCount").value(1))
                .andExpect(jsonPath("$.data.latestReleasePercent").value(62))
                .andExpect(jsonPath("$.data.latestTarget").value("상사"))
                .andExpect(jsonPath("$.data.weeklySessions").value(1))
                .andExpect(jsonPath("$.data.weeklyAverageRelease").value(62));
    }
}
