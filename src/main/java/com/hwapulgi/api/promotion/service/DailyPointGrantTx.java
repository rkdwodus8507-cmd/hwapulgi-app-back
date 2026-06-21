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
     * 당일 적립 행을 선점한다.
     * FAILED면 정당한 재시도로 보아 기존 행을 재사용하고, COMPLETED(이미 수령)나
     * REQUESTED(다른 요청이 처리 중)면 중복으로 거부한다. REQUESTED를 재사용하지 않는 것이
     * 동시 요청 시 이중 적립을 막는 핵심이다.
     * (프로세스가 reserve 직후 비정상 종료하면 REQUESTED 행이 남아 그날은 차단될 수 있으나,
     *  이중 적립보다 안전한 실패 모드다.)
     */
    @Transactional
    public DailyPointGrant reserveOrReuse(Long userId, LocalDate date, String promotionCode, int amount) {
        Optional<DailyPointGrant> existing = grantRepository.findByUserIdAndGrantDate(userId, date);
        if (existing.isPresent()) {
            DailyPointGrant grant = existing.get();
            if (grant.getStatus() != GrantStatus.FAILED) {
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
