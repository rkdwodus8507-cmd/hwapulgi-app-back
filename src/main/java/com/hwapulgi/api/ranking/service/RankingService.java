package com.hwapulgi.api.ranking.service;

import com.hwapulgi.api.ranking.dto.MyRankingResponse;
import com.hwapulgi.api.ranking.dto.RankingEntryResponse;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.hwapulgi.api.user.entity.User;

import java.time.LocalDate;
import java.time.Duration;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {

    private static final Duration WEEKLY_TTL = Duration.ofDays(14);
    private static final Duration MONTHLY_TTL = Duration.ofDays(62);

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public void addPoints(Long userId, int points) {
        String weeklyKey = buildKey("points", "weekly", currentWeekKey());
        String monthlyKey = buildKey("points", "monthly", currentMonthKey());
        String allTimeKey = buildKey("points", "all_time", "all");

        ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
        String member = String.valueOf(userId);
        ops.incrementScore(weeklyKey, member, points);
        ops.incrementScore(monthlyKey, member, points);
        ops.incrementScore(allTimeKey, member, points);

        setTtlIfAbsent(weeklyKey, WEEKLY_TTL);
        setTtlIfAbsent(monthlyKey, MONTHLY_TTL);
    }

    public void updateReleaseRate(Long userId, int releasePercent) {
        ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
        String member = String.valueOf(userId);

        String weeklyKey = buildKey("release", "weekly", currentWeekKey());
        String monthlyKey = buildKey("release", "monthly", currentMonthKey());

        updateIfHigher(ops, weeklyKey, member, releasePercent);
        updateIfHigher(ops, monthlyKey, member, releasePercent);
        updateIfHigher(ops, buildKey("release", "all_time", "all"), member, releasePercent);

        setTtlIfAbsent(weeklyKey, WEEKLY_TTL);
        setTtlIfAbsent(monthlyKey, MONTHLY_TTL);
    }

    private void setTtlIfAbsent(String key, Duration ttl) {
        Long currentTtl = redisTemplate.getExpire(key);
        if (currentTtl != null && currentTtl == -1) {
            redisTemplate.expire(key, ttl);
        }
    }

    private void updateIfHigher(ZSetOperations<String, String> ops, String key, String member, int score) {
        Double current = ops.score(key, member);
        if (current == null || score > current) {
            ops.add(key, member, score);
        }
    }

    public List<RankingEntryResponse> getTopRanking(String criteria, String period, int limit) {
        String periodKey = resolvePeriodKey(period);
        String key = buildKey(criteria, period, periodKey);

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = tuples.stream()
                .map(t -> Long.parseLong(t.getValue()))
                .toList();

        Map<Long, String> nicknameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        List<RankingEntryResponse> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long userId = Long.parseLong(tuple.getValue());
            String nickname = nicknameMap.getOrDefault(userId, "알 수 없음");
            result.add(new RankingEntryResponse(rank++, userId, nickname, tuple.getScore()));
        }
        return result;
    }

    public MyRankingResponse getMyRanking(Long userId, String criteria, String period) {
        String periodKey = resolvePeriodKey(period);
        String key = buildKey(criteria, period, periodKey);
        String member = String.valueOf(userId);

        Long rank = redisTemplate.opsForZSet().reverseRank(key, member);
        Double score = redisTemplate.opsForZSet().score(key, member);
        Long total = redisTemplate.opsForZSet().zCard(key);

        if (rank == null || score == null) {
            return new MyRankingResponse(0, total != null ? total : 0, 0);
        }
        return new MyRankingResponse(rank.intValue() + 1, total, score);
    }

    private String buildKey(String criteria, String period, String periodKey) {
        return "ranking:" + criteria + ":" + period + ":" + periodKey;
    }

    private String resolvePeriodKey(String period) {
        return switch (period) {
            case "weekly" -> currentWeekKey();
            case "monthly" -> currentMonthKey();
            default -> "all";
        };
    }

    public static String currentWeekKey() {
        LocalDate now = LocalDate.now();
        int week = now.get(WeekFields.of(Locale.KOREA).weekOfWeekBasedYear());
        int year = now.getYear();
        return year + "-W" + String.format("%02d", week);
    }

    public static String currentMonthKey() {
        LocalDate now = LocalDate.now();
        return now.getYear() + "-" + String.format("%02d", now.getMonthValue());
    }
}
