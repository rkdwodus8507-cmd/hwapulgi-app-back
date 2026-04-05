package com.hwapulgi.api.ranking.service;

import com.hwapulgi.api.ranking.dto.MyRankingResponse;
import com.hwapulgi.api.ranking.dto.RankingEntryResponse;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RankingService {

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
    }

    public void updateReleaseRate(Long userId, int releasePercent) {
        ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
        String member = String.valueOf(userId);

        updateIfHigher(ops, buildKey("release", "weekly", currentWeekKey()), member, releasePercent);
        updateIfHigher(ops, buildKey("release", "monthly", currentMonthKey()), member, releasePercent);
        updateIfHigher(ops, buildKey("release", "all_time", "all"), member, releasePercent);
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

        List<RankingEntryResponse> result = new ArrayList<>();
        int rank = 1;
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                Long userId = Long.parseLong(tuple.getValue());
                String nickname = userRepository.findById(userId)
                        .map(u -> u.getNickname())
                        .orElse("알 수 없음");
                result.add(new RankingEntryResponse(rank++, userId, nickname, tuple.getScore()));
            }
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
