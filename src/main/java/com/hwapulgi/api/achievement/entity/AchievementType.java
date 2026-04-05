package com.hwapulgi.api.achievement.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AchievementType {
    HITS_100("백 번의 주먹", "누적 100회 타격 달성", 100),
    HITS_500("오백 번의 분노", "누적 500회 타격 달성", 500),
    HITS_1000("천 번의 해소", "누적 1,000회 타격 달성", 1000),
    SESSIONS_10("열 번째 화풀기", "10회 화풀기 완료", 10),
    SESSIONS_50("화풀기 고수", "50회 화풀기 완료", 50),
    SESSIONS_100("화풀기 마스터", "100회 화풀기 완료", 100),
    RELEASE_80("마음이 편안", "해소율 80% 이상 달성", 80),
    RELEASE_90("거의 완벽", "해소율 90% 이상 달성", 90),
    RELEASE_100("완전 해소", "해소율 100% 달성", 100),
    STREAK_3("3일 연속", "3일 연속 화풀기", 3),
    STREAK_7("일주일 내내", "7일 연속 화풀기", 7),
    STREAK_30("한 달 연속", "30일 연속 화풀기", 30),
    POINTS_500("500점 돌파", "한 세션에서 500점 달성", 500),
    POINTS_1000("천점 달인", "한 세션에서 1,000점 달성", 1000);

    private final String title;
    private final String description;
    private final int threshold;
}
