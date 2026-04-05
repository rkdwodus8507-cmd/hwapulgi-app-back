package com.hwapulgi.api.ranking.service;

import com.hwapulgi.api.ranking.entity.PeriodType;
import com.hwapulgi.api.ranking.entity.RankingSnapshot;
import com.hwapulgi.api.ranking.repository.RankingSnapshotRepository;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSnapshotScheduler {

    private final StringRedisTemplate redisTemplate;
    private final RankingSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void snapshotWeekly() {
        String periodKey = RankingService.currentWeekKey();
        snapshot("points", "weekly", periodKey, PeriodType.WEEKLY);
        log.info("Weekly ranking snapshot saved: {}", periodKey);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void snapshotMonthly() {
        String periodKey = RankingService.currentMonthKey();
        snapshot("points", "monthly", periodKey, PeriodType.MONTHLY);
        log.info("Monthly ranking snapshot saved: {}", periodKey);
    }

    private void snapshot(String criteria, String period, String periodKey, PeriodType periodType) {
        String key = "ranking:" + criteria + ":" + period + ":" + periodKey;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);

        if (tuples == null) return;

        AtomicInteger rank = new AtomicInteger(1);
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long userId = Long.parseLong(tuple.getValue());
            int currentRank = rank.getAndIncrement();
            userRepository.findById(userId).ifPresent(user -> {
                RankingSnapshot snapshot = RankingSnapshot.builder()
                        .user(user)
                        .periodType(periodType)
                        .periodKey(periodKey)
                        .totalPoints(tuple.getScore().intValue())
                        .rank(currentRank)
                        .build();
                snapshotRepository.save(snapshot);
            });
        }
    }
}
