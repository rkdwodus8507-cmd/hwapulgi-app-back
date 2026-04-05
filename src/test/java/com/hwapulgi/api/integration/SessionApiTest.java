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
class SessionApiTest {

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
    void createAndGetSession() throws Exception {
        // points = 10 + 50 + (5*4) + (80-30)/2 = 105
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사", 80, 30, 50, 5, 62, 105, "화풀기 완료");

        String sessionJson = mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "1:테스트유저")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.points").value(105))
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(sessionJson).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target").value("회사"));
    }

    @Test
    void getRecentTargets_returnsDistinctCustomTargets() throws Exception {
        GameSessionCreateRequest req1 = new GameSessionCreateRequest(
                "기타", "팀장", "팀장", 70, 30, 40, 3, 57, 82, null);
        GameSessionCreateRequest req2 = new GameSessionCreateRequest(
                "기타", "부장", "부장", 60, 20, 30, 2, 66, 68, null);

        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "1:테스트유저")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "1:테스트유저")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/sessions/recent-targets")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getRecentNicknames_returnsDistinctNicknames() throws Exception {
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사", 80, 30, 50, 5, 62, 105, null);

        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "1:테스트유저")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/sessions/recent-nicknames")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("상사"));
    }

    @Test
    void createSession_invalidPoints_returns400() throws Exception {
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사", 80, 30, 50, 5, 62, 9999, null);

        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "1:테스트유저")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
