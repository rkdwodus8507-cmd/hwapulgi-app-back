package com.hwapulgi.api.ranking.repository;

import com.hwapulgi.api.ranking.entity.PeriodType;
import com.hwapulgi.api.ranking.entity.RankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {
    Optional<RankingSnapshot> findByUserIdAndPeriodTypeAndPeriodKey(Long userId, PeriodType periodType, String periodKey);
}
