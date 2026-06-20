package com.hwapulgi.api.promotion.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 일일 적립 행의 상태 전이를 각각 독립 트랜잭션으로 커밋한다.
 * {@link PromotionService#claimDaily}는 외부 토스 호출을 트랜잭션 밖에서 수행하므로,
 * 예약(REQUESTED)·완료(COMPLETED)·실패(FAILED)가 토스 호출 성패와 무관하게 DB에 남는다.
 */
@Component
@RequiredArgsConstructor
public class DailyPointGrantTx {

    private final DailyPointGrantRepository grantRepository;

    /**
     * 당일 적립 행을 선점한다. 이미 COMPLETED면 중복 수령으로 거부하고,
     * REQUESTED/FAILED면 재시도를 위해 기존 행을 재사용한다.
     */
    @Transactional
    public DailyPointGrant reserveOrReuse(Long userId, LocalDate date, String promotionCode, int amount) {
        Optional<DailyPointGrant> existing = grantRepository.findByUserIdAndGrantDate(userId, date);
        if (existing.isPresent()) {
            DailyPointGrant grant = existing.get();
            if (grant.getStatus() == GrantStatus.COMPLETED) {
                throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
            }
            return grant;
        }
        try {
            return grantRepository.saveAndFlush(
                    DailyPointGrant.builder()
                            .userId(userId)
                            .grantDate(date)
                            .promotionCode(promotionCode)
                            .amount(amount)
                            .build());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 선점함
            throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
        }
    }

    @Transactional
    public void markCompleted(Long grantId, String tossKey) {
        grantRepository.findById(grantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROMOTION_FAILED))
                .markCompleted(tossKey);
    }

    @Transactional
    public void markFailed(Long grantId) {
        grantRepository.findById(grantId).ifPresent(DailyPointGrant::markFailed);
    }
}
