package com.hwapulgi.api.report.service;

import com.hwapulgi.api.report.dto.WeeklyArchiveResponse;
import com.hwapulgi.api.report.dto.WeeklySummaryResponse;
import com.hwapulgi.api.report.dto.WeeklySummaryResponse.CalendarDay;
import com.hwapulgi.api.report.dto.WeeklySummaryResponse.SessionSummary;
import com.hwapulgi.api.report.dto.WeeklySummaryResponse.TopTarget;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final String[] WEEKDAY_LABELS = {"일", "월", "화", "수", "목", "금", "토"};
    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final GameSessionRepository gameSessionRepository;
    private final Clock clock;

    public WeeklySummaryResponse getWeeklySummary(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return buildWeeklySummary(userId, today);
    }

    public List<WeeklyArchiveResponse> getWeeklyArchives(Long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<GameSession> allSessions = gameSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<LocalDate, List<GameSession>> grouped = allSessions.stream()
                .collect(Collectors.groupingBy(s ->
                        s.getCreatedAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))));

        return grouped.entrySet().stream()
                .filter(e -> e.getKey().isBefore(currentWeekStart))
                .sorted(Comparator.<Map.Entry<LocalDate, List<GameSession>>, LocalDate>comparing(Map.Entry::getKey).reversed())
                .map(e -> buildArchive(e.getKey(), e.getValue()))
                .toList();
    }

    private WeeklyArchiveResponse buildArchive(LocalDate weekStart, List<GameSession> sessions) {
        LocalDate weekEnd = weekStart.plusDays(6);
        int totalSessions = sessions.size();
        int totalHits = sessions.stream().mapToInt(GameSession::getHits).sum();
        int totalReleased = sessions.stream()
                .mapToInt(s -> Math.max(0, s.getAngerBefore() - s.getAngerAfter())).sum();
        int totalBefore = sessions.stream().mapToInt(GameSession::getAngerBefore).sum();
        int totalAfter = sessions.stream().mapToInt(GameSession::getAngerAfter).sum();
        int averageRelease = (int) sessions.stream().mapToInt(GameSession::getReleasedPercent).average().orElse(0);

        List<TopTarget> topTargets = buildTopTargets(sessions);
        String topTarget = topTargets.isEmpty() ? "-" : topTargets.get(0).getLabel();
        String hardestWeekday = findHardestWeekday(sessions);

        String periodText = weekStart.getMonthValue() + "." + weekStart.getDayOfMonth()
                + " - " + weekEnd.getMonthValue() + "." + weekEnd.getDayOfMonth();

        return WeeklyArchiveResponse.builder()
                .id(weekStart.format(DATE_KEY_FORMAT))
                .label(buildWeekLabel(weekStart))
                .periodText(periodText)
                .totalSessions(totalSessions)
                .totalHits(totalHits)
                .totalReleased(totalReleased)
                .averageBefore(Math.round((float) totalBefore / totalSessions))
                .averageAfter(Math.round((float) totalAfter / totalSessions))
                .averageRelease(averageRelease)
                .topTarget(topTarget)
                .hardestWeekday(hardestWeekday)
                .build();
    }

    private WeeklySummaryResponse buildWeeklySummary(Long userId, LocalDate referenceDate) {
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = referenceDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        String label = buildWeekLabel(referenceDate);

        List<GameSession> sessions = gameSessionRepository.findByUserIdAndCreatedAtAfter(
                userId, weekStart.atStartOfDay());
        List<GameSession> weeklySessions = sessions.stream()
                .filter(s -> !s.getCreatedAt().toLocalDate().isAfter(weekEnd))
                .toList();

        if (weeklySessions.isEmpty()) {
            return buildEmptySummary(label, weekStart);
        }

        int totalSessions = weeklySessions.size();
        int totalHits = weeklySessions.stream().mapToInt(GameSession::getHits).sum();
        int totalBefore = weeklySessions.stream().mapToInt(GameSession::getAngerBefore).sum();
        int totalAfter = weeklySessions.stream().mapToInt(GameSession::getAngerAfter).sum();
        int totalReleased = weeklySessions.stream()
                .mapToInt(s -> Math.max(0, s.getAngerBefore() - s.getAngerAfter()))
                .sum();
        int bestRelease = weeklySessions.stream().mapToInt(GameSession::getReleasedPercent).max().orElse(0);
        int averageRelease = (int) weeklySessions.stream().mapToInt(GameSession::getReleasedPercent).average().orElse(0);

        List<TopTarget> topTargets = buildTopTargets(weeklySessions);
        String hardestWeekday = findHardestWeekday(weeklySessions);
        List<CalendarDay> calendarDays = buildCalendarDays(weeklySessions, weekStart);
        int streakDays = calculateStreakDays(calendarDays);

        String primaryTarget = topTargets.isEmpty() ? null : topTargets.get(0).getLabel();
        String headline = primaryTarget != null
                ? hardestWeekday + "에 " + primaryTarget + " 때문에 가장 힘들었어요."
                : hardestWeekday + "에 감정 기복이 가장 컸어요.";

        return WeeklySummaryResponse.builder()
                .label(label)
                .totalSessions(totalSessions)
                .totalHits(totalHits)
                .totalReleased(totalReleased)
                .averageBefore(Math.round((float) totalBefore / totalSessions))
                .averageAfter(Math.round((float) totalAfter / totalSessions))
                .averageRelease(averageRelease)
                .bestRelease(bestRelease)
                .hardestWeekday(hardestWeekday)
                .streakDays(streakDays)
                .weeklyHeadline(headline)
                .topTargets(topTargets)
                .calendarDays(calendarDays)
                .build();
    }

    private WeeklySummaryResponse buildEmptySummary(String label, LocalDate weekStart) {
        return WeeklySummaryResponse.builder()
                .label(label)
                .totalSessions(0).totalHits(0).totalReleased(0)
                .averageBefore(0).averageAfter(0).averageRelease(0).bestRelease(0)
                .hardestWeekday("-").streakDays(0)
                .weeklyHeadline("이번 주 감정 기록을 시작해보세요.")
                .topTargets(List.of())
                .calendarDays(buildCalendarDays(List.of(), weekStart))
                .build();
    }

    private List<TopTarget> buildTopTargets(List<GameSession> sessions) {
        return sessions.stream()
                .collect(Collectors.groupingBy(this::formatLabel, Collectors.counting()))
                .entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>, Long>comparing(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(e -> new TopTarget(e.getKey(), e.getValue()))
                .toList();
    }

    private String findHardestWeekday(List<GameSession> sessions) {
        Map<DayOfWeek, Integer> scores = new EnumMap<>(DayOfWeek.class);
        for (GameSession s : sessions) {
            DayOfWeek dow = s.getCreatedAt().getDayOfWeek();
            scores.merge(dow, s.getAngerBefore(), Integer::sum);
        }
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> WEEKDAY_LABELS[e.getKey().getValue() % 7] + "요일")
                .orElse("-");
    }

    private List<CalendarDay> buildCalendarDays(List<GameSession> sessions, LocalDate weekStart) {
        Map<LocalDate, List<GameSession>> byDate = sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getCreatedAt().toLocalDate()));

        List<CalendarDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            List<GameSession> daySessions = byDate.getOrDefault(date, List.of());
            int javaDay = date.getDayOfWeek().getValue() % 7; // 월=1..일=0 맞추기 위해
            String dayLabel = WEEKDAY_LABELS[javaDay];

            List<SessionSummary> summaries = daySessions.stream()
                    .sorted(Comparator.comparing(GameSession::getCreatedAt).reversed())
                    .map(s -> new SessionSummary(
                            s.getId().toString(), formatLabel(s),
                            s.getAngerBefore(), s.getAngerAfter(),
                            s.getHits(), s.getReleasedPercent(),
                            s.getCreatedAt().toString(),
                            s.getMemo()))
                    .toList();

            days.add(new CalendarDay(
                    date.format(DATE_KEY_FORMAT), dayLabel, date.getDayOfMonth(),
                    calculateAngerLevel(daySessions), summaries));
        }
        return days;
    }

    private int calculateAngerLevel(List<GameSession> sessions) {
        if (sessions.isEmpty()) return 0;
        double avgBefore = sessions.stream().mapToInt(GameSession::getAngerBefore).average().orElse(0);
        int sessionWeight = Math.max(0, sessions.size() - 1) * 8;
        return Math.min(100, (int) Math.round(avgBefore + sessionWeight));
    }

    private int calculateStreakDays(List<CalendarDay> calendarDays) {
        int streak = 0;
        for (int i = calendarDays.size() - 1; i >= 0; i--) {
            if (calendarDays.get(i).getSessions().isEmpty()) break;
            streak++;
        }
        return streak;
    }

    private String buildWeekLabel(LocalDate date) {
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate firstWeekStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate currentWeekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekIndex = (int) ((currentWeekStart.toEpochDay() - firstWeekStart.toEpochDay()) / 7) + 1;
        return date.getMonthValue() + "월 " + weekIndex + "주차";
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
