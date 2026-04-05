package com.hwapulgi.api.session.service;

public class PointsCalculator {

    public static int calculate(int hits, int skillShots, int angerBefore, int angerAfter) {
        int effectiveAfter = Math.min(angerAfter, angerBefore);
        return 10 + hits + (skillShots * 4) + (angerBefore - effectiveAfter) / 2;
    }
}
