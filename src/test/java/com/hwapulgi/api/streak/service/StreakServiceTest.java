package com.hwapulgi.api.streak.service;

import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.streak.dto.StreakResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock private GameSessionRepository gameSessionRepository;
    @InjectMocks private StreakService streakService;

    @Test
    void getStreak_consecutiveDays_calculatesCorrectly() {
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = List.of(today, today.minusDays(1), today.minusDays(2));
        given(gameSessionRepository.findDistinctPlayDatesByUserId(1L)).willReturn(dates);

        StreakResponse result = streakService.getStreak(1L);
        assertThat(result.getCurrentStreak()).isEqualTo(3);
        assertThat(result.getBestStreak()).isEqualTo(3);
        assertThat(result.getTotalPlayDays()).isEqualTo(3);
    }

    @Test
    void getStreak_brokenStreak_returnsZeroCurrent() {
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = List.of(today.minusDays(3), today.minusDays(4));
        given(gameSessionRepository.findDistinctPlayDatesByUserId(1L)).willReturn(dates);

        StreakResponse result = streakService.getStreak(1L);
        assertThat(result.getCurrentStreak()).isEqualTo(0);
        assertThat(result.getBestStreak()).isEqualTo(2);
    }

    @Test
    void getStreak_noSessions_allZero() {
        given(gameSessionRepository.findDistinctPlayDatesByUserId(1L)).willReturn(List.of());

        StreakResponse result = streakService.getStreak(1L);
        assertThat(result.getCurrentStreak()).isEqualTo(0);
        assertThat(result.getBestStreak()).isEqualTo(0);
    }

    @Test
    void getStreak_gapInMiddle_bestStreakHigherThanCurrent() {
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = List.of(
                today, today.minusDays(1),
                today.minusDays(5), today.minusDays(6), today.minusDays(7), today.minusDays(8));
        given(gameSessionRepository.findDistinctPlayDatesByUserId(1L)).willReturn(dates);

        StreakResponse result = streakService.getStreak(1L);
        assertThat(result.getCurrentStreak()).isEqualTo(2);
        assertThat(result.getBestStreak()).isEqualTo(4);
    }
}
