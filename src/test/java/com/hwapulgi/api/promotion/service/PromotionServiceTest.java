package com.hwapulgi.api.promotion.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.promotion.client.PromotionClient;
import com.hwapulgi.api.promotion.dto.DailyClaimResponse;
import com.hwapulgi.api.promotion.dto.DailyStatusResponse;
import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private DailyPointGrantRepository grantRepository;
    @Mock private PromotionClient promotionClient;
    @Mock private UserService userService;

    private PromotionService service;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-06-20T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDate today = LocalDate.of(2026, 6, 20);

    @BeforeEach
    void setUp() {
        service = new PromotionService(grantRepository, promotionClient, userService,
                fixedClock, "DAILY", 100);
    }

    @Test
    void claimDaily_firstClaim_succeeds() {
        given(grantRepository.findByUserIdAndGrantDate(1L, today)).willReturn(Optional.empty());
        given(userService.findById(1L)).willReturn(User.tossUser("12345", "닉"));
        given(grantRepository.saveAndFlush(any(DailyPointGrant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(promotionClient.generateKey("12345")).willReturn("KEY_ABC");

        DailyClaimResponse response = service.claimDaily(1L);

        assertThat(response.granted()).isTrue();
        assertThat(response.amount()).isEqualTo(100);
        verify(promotionClient).executePromotion("12345", "DAILY", "KEY_ABC", 100);
    }

    @Test
    void claimDaily_alreadyCompletedToday_throwsAndSkipsToss() {
        DailyPointGrant completed = DailyPointGrant.builder()
                .userId(1L).grantDate(today).promotionCode("DAILY").amount(100).build();
        completed.markCompleted("KEY_OLD");
        given(grantRepository.findByUserIdAndGrantDate(1L, today))
                .willReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.claimDaily(1L))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(promotionClient);
    }

    @Test
    void claimDaily_tossFails_propagatesException() {
        given(grantRepository.findByUserIdAndGrantDate(1L, today)).willReturn(Optional.empty());
        given(userService.findById(1L)).willReturn(User.tossUser("12345", "닉"));
        given(grantRepository.saveAndFlush(any(DailyPointGrant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(promotionClient.generateKey("12345"))
                .willThrow(new BusinessException(com.hwapulgi.api.common.exception.ErrorCode.PROMOTION_FAILED));

        assertThatThrownBy(() -> service.claimDaily(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getDailyStatus_notClaimedToday_claimableTrue() {
        given(grantRepository.findByUserIdAndGrantDate(1L, today)).willReturn(Optional.empty());
        given(grantRepository.findTopByUserIdAndStatusOrderByGrantDateDesc(1L, GrantStatus.COMPLETED))
                .willReturn(Optional.empty());

        DailyStatusResponse status = service.getDailyStatus(1L);

        assertThat(status.claimable()).isTrue();
        assertThat(status.amount()).isEqualTo(100);
        assertThat(status.lastClaimedAt()).isNull();
    }
}
