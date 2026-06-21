package com.hwapulgi.api.integration;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.promotion.client.PromotionClient;
import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import com.hwapulgi.api.promotion.service.PromotionService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 동시 수령 회귀 테스트.
 * (user_id, grant_date) 유니크 제약 + DataIntegrityViolation→ALREADY_CLAIMED 처리,
 * 그리고 REQUESTED 행을 재사용하지 않는 reserveOrReuse 정책이 함께 동작해
 * 같은 유저가 동시에 여러 번 수령을 시도해도 정확히 1회만 적립되는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class PromotionConcurrencyTest {

    @Autowired private PromotionService promotionService;
    @Autowired private UserRepository userRepository;
    @Autowired private DailyPointGrantRepository grantRepository;

    @MockitoBean private PromotionClient promotionClient;

    private Long userId;

    @BeforeEach
    void setUp() {
        grantRepository.deleteAll();
        userRepository.deleteAll();
        userId = userRepository.save(User.tossUser("12345", "테스트유저")).getId();

        given(promotionClient.generateKey(anyString())).willReturn("KEY_ABC");
        // executePromotion 은 void → 기본적으로 아무것도 하지 않음(성공)
    }

    @Test
    void concurrentClaims_grantExactlyOnce() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyClaimed = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    promotionService.claimDaily(userId);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ALREADY_CLAIMED) {
                        alreadyClaimed.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown(); // 동시에 출발
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 정확히 1건만 성공, 나머지는 모두 중복 거부
        assertThat(success.get()).isEqualTo(1);
        assertThat(alreadyClaimed.get()).isEqualTo(threads - 1);
        assertThat(unexpected.get()).isZero();

        // 행은 1개뿐이고 COMPLETED
        List<DailyPointGrant> grants = grantRepository.findAll();
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).getStatus()).isEqualTo(GrantStatus.COMPLETED);

        // 외부 적립 호출도 정확히 1회
        verify(promotionClient, times(1)).executePromotion(anyString(), anyString(), anyString(), anyInt());
    }

    /**
     * 시차 도착(이중 적립) 경로를 결정적으로 검증한다.
     * 다른 요청이 이미 선점한(또는 크래시로 남은) 당일 REQUESTED 행이 있으면,
     * 재사용하지 않고 중복으로 거부해 두 번째 적립을 막아야 한다.
     */
    @Test
    void inFlightRequestedRow_isRejectedNotReused() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        grantRepository.save(DailyPointGrant.builder()
                .userId(userId)
                .grantDate(today)
                .promotionCode("DAILY_ATTENDANCE")
                .amount(100)
                .build()); // status = REQUESTED

        assertThatThrownBy(() -> promotionService.claimDaily(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_CLAIMED);

        // 토스 적립이 호출되면 안 됨 (이중 적립 방지)
        verify(promotionClient, never()).executePromotion(anyString(), anyString(), anyString(), anyInt());
        // 기존 REQUESTED 행은 그대로 1건
        List<DailyPointGrant> grants = grantRepository.findAll();
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).getStatus()).isEqualTo(GrantStatus.REQUESTED);
    }
}
