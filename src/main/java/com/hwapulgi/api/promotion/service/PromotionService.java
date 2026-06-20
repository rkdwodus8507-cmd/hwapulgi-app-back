package com.hwapulgi.api.promotion.service;

import com.hwapulgi.api.promotion.client.PromotionClient;
import com.hwapulgi.api.promotion.dto.DailyClaimResponse;
import com.hwapulgi.api.promotion.dto.DailyStatusResponse;
import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PromotionService {

    private final DailyPointGrantRepository grantRepository;
    private final DailyPointGrantTx grantTx;
    private final PromotionClient promotionClient;
    private final UserService userService;
    private final Clock clock;
    private final String promotionCode;
    private final int amount;

    @Autowired
    public PromotionService(
            DailyPointGrantRepository grantRepository,
            DailyPointGrantTx grantTx,
            PromotionClient promotionClient,
            UserService userService,
            @Value("${appintoss.promotion.daily.code}") String promotionCode,
            @Value("${appintoss.promotion.daily.amount}") int amount) {
        this(grantRepository, grantTx, promotionClient, userService,
                Clock.system(ZoneId.of("Asia/Seoul")), promotionCode, amount);
    }

    PromotionService(
            DailyPointGrantRepository grantRepository,
            DailyPointGrantTx grantTx,
            PromotionClient promotionClient,
            UserService userService,
            Clock clock,
            String promotionCode,
            int amount) {
        this.grantRepository = grantRepository;
        this.grantTx = grantTx;
        this.promotionClient = promotionClient;
        this.userService = userService;
        this.clock = clock;
        this.promotionCode = promotionCode;
        this.amount = amount;
    }

    /**
     * 일일 출석 포인트 수령. 외부 토스 호출은 트랜잭션 밖에서 수행하고,
     * 예약/완료/실패 상태 전이는 {@link DailyPointGrantTx}의 독립 트랜잭션으로 커밋한다.
     * 토스 호출이 실패하면 행은 FAILED로 남아 감사 추적 및 같은 날 재시도가 가능하다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DailyClaimResponse claimDaily(Long userId) {
        LocalDate today = LocalDate.now(clock);
        User user = userService.findById(userId);

        DailyPointGrant grant = grantTx.reserveOrReuse(userId, today, promotionCode, amount);

        String key;
        try {
            key = promotionClient.generateKey(user.getExternalId());
            promotionClient.executePromotion(user.getExternalId(), promotionCode, key, amount);
        } catch (RuntimeException e) {
            grantTx.markFailed(grant.getId());
            throw e;
        }

        grantTx.markCompleted(grant.getId(), key);
        return new DailyClaimResponse(true, amount, grant.getCreatedAt());
    }

    public DailyStatusResponse getDailyStatus(Long userId) {
        LocalDate today = LocalDate.now(clock);

        boolean claimable = grantRepository.findByUserIdAndGrantDate(userId, today)
                .map(g -> g.getStatus() != GrantStatus.COMPLETED)
                .orElse(true);

        LocalDateTime lastClaimedAt = grantRepository
                .findTopByUserIdAndStatusOrderByGrantDateDesc(userId, GrantStatus.COMPLETED)
                .map(DailyPointGrant::getCreatedAt)
                .orElse(null);

        return new DailyStatusResponse(claimable, amount, lastClaimedAt);
    }
}
