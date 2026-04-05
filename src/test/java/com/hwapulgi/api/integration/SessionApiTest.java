package com.hwapulgi.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

    @BeforeEach
    void setUp() {
        gameSessionRepository.deleteAll();
        userRepository.deleteAll();
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

        mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target").value("회사"));
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
