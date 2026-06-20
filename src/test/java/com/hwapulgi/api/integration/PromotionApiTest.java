package com.hwapulgi.api.integration;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.integration.support.AuthTokenFixture;
import com.hwapulgi.api.promotion.client.PromotionClient;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(AuthTokenFixture.class)
class PromotionApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private DailyPointGrantRepository grantRepository;
    @Autowired private AuthTokenFixture authTokens;

    @MockBean private PromotionClient promotionClient;

    private String bearer;

    @BeforeEach
    void setUp() {
        grantRepository.deleteAll();
        userRepository.deleteAll();
        User user = userRepository.save(User.tossUser("12345", "테스트유저"));
        bearer = authTokens.bearerForUser(user);

        given(promotionClient.generateKey("12345")).willReturn("KEY_ABC");
        doNothing().when(promotionClient).executePromotion("12345", "DAILY_ATTENDANCE", "KEY_ABC", 100);
    }

    @Test
    void claimDaily_firstTime_succeedsThenConflict() throws Exception {
        mockMvc.perform(post("/api/v1/promotion/daily/claim").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.granted").value(true))
                .andExpect(jsonPath("$.data.amount").value(100));

        // 같은 날 재수령 → 409
        mockMvc.perform(post("/api/v1/promotion/daily/claim").header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void status_reflectsClaim() throws Exception {
        mockMvc.perform(get("/api/v1/promotion/daily/status").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimable").value(true));

        mockMvc.perform(post("/api/v1/promotion/daily/claim").header("Authorization", bearer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/promotion/daily/status").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimable").value(false));
    }

    @Test
    void claimDaily_tossFails_persistsFailedRowThenRetrySucceeds() throws Exception {
        // 토스 적립 호출이 실패
        doThrow(new BusinessException(ErrorCode.PROMOTION_FAILED))
                .when(promotionClient).executePromotion("12345", "DAILY_ATTENDANCE", "KEY_ABC", 100);

        mockMvc.perform(post("/api/v1/promotion/daily/claim").header("Authorization", bearer))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));

        // 실패해도 FAILED 행이 커밋되어 남아야 함
        var afterFail = grantRepository.findAll();
        assertThat(afterFail).hasSize(1);
        assertThat(afterFail.get(0).getStatus()).isEqualTo(GrantStatus.FAILED);

        // 재시도: 이번엔 성공 → 기존 행을 재사용해 COMPLETED (새 행 생성 X)
        doNothing().when(promotionClient).executePromotion("12345", "DAILY_ATTENDANCE", "KEY_ABC", 100);

        mockMvc.perform(post("/api/v1/promotion/daily/claim").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.granted").value(true));

        var afterRetry = grantRepository.findAll();
        assertThat(afterRetry).hasSize(1);
        assertThat(afterRetry.get(0).getStatus()).isEqualTo(GrantStatus.COMPLETED);
    }
}
