package com.hwapulgi.api.integration;

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
class RankingApiTest {

    @Autowired
    private MockMvc mockMvc;

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
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").value(0));
    }
}
