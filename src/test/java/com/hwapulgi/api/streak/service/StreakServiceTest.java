package com.hwapulgi.api.streak.service;

import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.streak.dto.StreakResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock private GameSessionRepository gameSessionRepository;

    private StreakService streakService;

    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 4, 5);

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                FIXED_TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        streakService = new StreakService(gameSessionRepository, fixedClock);
    }

    @Test
    void getStreak_consecutiveDays_calculatesCorrectly() {
        List<LocalDate> dates = List.of(FIXED_TODAY, FIXED_TODAY.minusDays(1), FIXED_TODAY.minusDays(2));
        given(gameSessionRepository.findDistinctPlayDatesByUserId(1L)).willReturn(dates);

        StreakResponse result = streakService.getStreak(1L);
        assertThat(result.getCurrentStreak()).isEqualTo(3);
        assertThat(result.getBestStreak()).isEqualTo(3);
        assertThat(result.getTotalPlayDays()).isEqualTo(3);
    }

    @Test
    void getStreak_brokenStreak_returnsZeroCurrent() {
        List<LocalDate> dates = List.of(FIXED_TODAY.minusDays(3), FIXED_TODAY.minusDays(4));
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
        List<LocalDate> dates = List.of(
                FIXED_TODAY, FIXED_TODAY.minusDays(1),
                FIXED_TODAY.minusDays(5), FIXED_TODAY.minusDays(6),
                FIXED_TODAY.minusDays(7), FIXED_TODAY.minusDays(8));
        given(gameSessionRepository.findDistinctPlayDatesByUserId(1L)).willReturn(dates);

        StreakResponse result = streakService.getStreak(1L);
        assertThat(result.getCurrentStreak()).isEqualTo(2);
        assertThat(result.getBestStreak()).isEqualTo(4);
    }
}
