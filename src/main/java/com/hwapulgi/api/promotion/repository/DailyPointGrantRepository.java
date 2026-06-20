package com.hwapulgi.api.promotion.repository;

import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyPointGrantRepository extends JpaRepository<DailyPointGrant, Long> {

    Optional<DailyPointGrant> findByUserIdAndGrantDate(Long userId, LocalDate grantDate);

    Optional<DailyPointGrant> findTopByUserIdAndStatusOrderByGrantDateDesc(Long userId, GrantStatus status);
}
