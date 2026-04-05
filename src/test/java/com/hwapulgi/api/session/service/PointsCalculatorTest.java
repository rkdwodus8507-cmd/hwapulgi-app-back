package com.hwapulgi.api.session.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PointsCalculatorTest {

    @Test
    void calculate_normalCase() {
        // 10 + 50 + (5*4) + (80-30)/2 = 105
        assertThat(PointsCalculator.calculate(50, 5, 80, 30)).isEqualTo(105);
    }

    @Test
    void calculate_angerAfterHigherThanBefore_usesBeforeAsFloor() {
        // angerAfter(90) > angerBefore(50), effectiveAfter=50 → 10+10+8+0=28
        assertThat(PointsCalculator.calculate(10, 2, 50, 90)).isEqualTo(28);
    }

    @Test
    void calculate_zeroHitsAndSkills() {
        // 10 + 0 + 0 + (100-0)/2 = 60
        assertThat(PointsCalculator.calculate(0, 0, 100, 0)).isEqualTo(60);
    }

    @Test
    void calculate_allZero() {
        assertThat(PointsCalculator.calculate(0, 0, 0, 0)).isEqualTo(10);
    }
}
