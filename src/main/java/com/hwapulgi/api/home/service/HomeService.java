package com.hwapulgi.api.home.service;

import com.hwapulgi.api.home.dto.HomeSnapshotResponse;
import com.hwapulgi.api.session.dto.TargetStatsResponse;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final GameSessionRepository gameSessionRepository;
    private final Clock clock;

    public HomeSnapshotResponse getSnapshot(Long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        int todayCount = (int) gameSessionRepository.countByUserIdAndCreatedAtBetween(userId, todayStart, todayEnd);

        GameSession latestSession = gameSessionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElse(null);
        int latestReleasePercent = latestSession != null ? latestSession.getReleasedPercent() : 0;
        String latestTarget = latestSession != null ? formatLabel(latestSession) : "-";

        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1).atStartOfDay();
        List<GameSession> weeklySessions = gameSessionRepository.findByUserIdAndCreatedAtAfter(userId, weekStart);
        List<GameSession> weeklyFiltered = weeklySessions.stream()
                .filter(s -> s.getCreatedAt().isBefore(weekEnd))
                .toList();

        int weeklySessionCount = weeklyFiltered.size();
        int weeklyAverageRelease = weeklyFiltered.isEmpty() ? 0
                : (int) weeklyFiltered.stream().mapToInt(GameSession::getReleasedPercent).average().orElse(0);

        String primaryTarget = weeklyFiltered.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        this::formatLabel, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("-");

        return new HomeSnapshotResponse(
                todayCount, latestReleasePercent, latestTarget,
                primaryTarget, weeklySessionCount, weeklyAverageRelease);
    }

    private String formatLabel(GameSession session) {
        if (session.getCustomTarget() != null && !session.getCustomTarget().isBlank()) {
            return session.getCustomTarget().trim();
        }
        if (!session.getTargetNickname().isBlank()) {
            return session.getTargetNickname().trim();
        }
        return session.getTarget();
    }
}
