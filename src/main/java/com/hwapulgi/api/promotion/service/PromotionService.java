package com.hwapulgi.api.promotion.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.promotion.client.PromotionClient;
import com.hwapulgi.api.promotion.dto.DailyClaimResponse;
import com.hwapulgi.api.promotion.dto.DailyStatusResponse;
import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PromotionService {

    private final DailyPointGrantRepository grantRepository;
    private final PromotionClient promotionClient;
    private final UserService userService;
    private final Clock clock;
    private final String promotionCode;
    private final int amount;

    public PromotionService(
            DailyPointGrantRepository grantRepository,
            PromotionClient promotionClient,
            UserService userService,
            @Value("${appintoss.promotion.daily.code}") String promotionCode,
            @Value("${appintoss.promotion.daily.amount}") int amount) {
        this(grantRepository, promotionClient, userService,
                Clock.system(ZoneId.of("Asia/Seoul")), promotionCode, amount);
    }

    PromotionService(
            DailyPointGrantRepository grantRepository,
            PromotionClient promotionClient,
            UserService userService,
            Clock clock,
            String promotionCode,
            int amount) {
        this.grantRepository = grantRepository;
        this.promotionClient = promotionClient;
        this.userService = userService;
        this.clock = clock;
        this.promotionCode = promotionCode;
        this.amount = amount;
    }

    @Transactional
    public DailyClaimResponse claimDaily(Long userId) {
        LocalDate today = LocalDate.now(clock);

        Optional<DailyPointGrant> existing = grantRepository.findByUserIdAndGrantDate(userId, today);
        if (existing.isPresent() && existing.get().getStatus() == GrantStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
        }

        User user = userService.findById(userId);

        DailyPointGrant grant = existing.orElseGet(() -> reserve(userId, today));

        String key = promotionClient.generateKey(user.getExternalId());
        promotionClient.executePromotion(user.getExternalId(), promotionCode, key, amount);
        grant.markCompleted(key);

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

    private DailyPointGrant reserve(Long userId, LocalDate today) {
        try {
            return grantRepository.saveAndFlush(
                    DailyPointGrant.builder()
                            .userId(userId)
                            .grantDate(today)
                            .promotionCode(promotionCode)
                            .amount(amount)
                            .build());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 선점함
            throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
        }
    }
}
