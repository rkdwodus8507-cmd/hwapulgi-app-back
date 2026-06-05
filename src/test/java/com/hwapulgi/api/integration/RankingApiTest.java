package com.hwapulgi.api.integration;

import com.hwapulgi.api.integration.support.AuthTokenFixture;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(AuthTokenFixture.class)
class RankingApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthTokenFixture authTokens;

    private User testUser;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        userRepository.deleteAll();
        testUser = userRepository.save(User.tossUser("1", "테스트유저"));
    }

    @Test
    void getPointsRanking_emptyList() throws Exception {
        mockMvc.perform(get("/api/v1/rankings/points")
                        .param("period", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMyRanking_noData() throws Exception {
        mockMvc.perform(get("/api/v1/rankings/me")
                        .header("Authorization", authTokens.bearerForUser(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").value(0));
    }
}
