package com.hwapulgi.api.streak.service;

import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.streak.dto.StreakResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreakService {

    private final GameSessionRepository gameSessionRepository;
    private final Clock clock;

    public StreakResponse getStreak(Long userId) {
        List<LocalDate> playDates = gameSessionRepository.findDistinctPlayDatesByUserId(userId);
        if (playDates.isEmpty()) {
            return new StreakResponse(0, 0, 0);
        }
        int currentStreak = calculateCurrentStreak(playDates);
        int bestStreak = calculateBestStreak(playDates);
        return new StreakResponse(currentStreak, bestStreak, playDates.size());
    }

    public int getCurrentStreak(Long userId) {
        List<LocalDate> playDates = gameSessionRepository.findDistinctPlayDatesByUserId(userId);
        if (playDates.isEmpty()) return 0;
        return calculateCurrentStreak(playDates);
    }

    private int calculateCurrentStreak(List<LocalDate> sortedDatesDesc) {
        LocalDate today = LocalDate.now(clock);
        LocalDate latest = sortedDatesDesc.get(0);
        if (latest.isBefore(today.minusDays(1))) {
            return 0;
        }
        int streak = 1;
        for (int i = 1; i < sortedDatesDesc.size(); i++) {
            LocalDate prev = sortedDatesDesc.get(i - 1);
            LocalDate curr = sortedDatesDesc.get(i);
            if (prev.minusDays(1).equals(curr)) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private int calculateBestStreak(List<LocalDate> sortedDatesDesc) {
        int best = 1;
        int current = 1;
        for (int i = 1; i < sortedDatesDesc.size(); i++) {
            LocalDate prev = sortedDatesDesc.get(i - 1);
            LocalDate curr = sortedDatesDesc.get(i);
            if (prev.minusDays(1).equals(curr)) {
                current++;
                best = Math.max(best, current);
            } else {
                current = 1;
            }
        }
        return best;
    }
}
