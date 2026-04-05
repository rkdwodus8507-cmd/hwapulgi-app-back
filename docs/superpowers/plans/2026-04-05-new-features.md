# Achievement + Streak + Target Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 업적/배지 시스템, 연속 플레이 스트릭, 분노 대상별 통계 3개 기능 추가

**Architecture:** 기존 session/user 도메인에 achievement, streak 패키지 추가. 대상 통계는 game_sessions 테이블 집계 쿼리로 처리. 업적은 세션 생성 시 자동 체크, 스트릭은 세션의 날짜를 기반으로 계산.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Lombok, JUnit 5

---

## File Structure

```
src/main/java/com/hwapulgi/api/
├── achievement/
│   ├── entity/
│   │   ├── Achievement.java              (JPA 엔티티)
│   │   └── AchievementType.java          (enum)
│   ├── repository/
│   │   └── AchievementRepository.java
│   ├── service/
│   │   └── AchievementService.java
│   ├── controller/
│   │   └── AchievementController.java
│   └── dto/
│       └── AchievementResponse.java
├── streak/
│   ├── service/
│   │   └── StreakService.java
│   ├── controller/
│   │   └── StreakController.java
│   └── dto/
│       └── StreakResponse.java
├── session/
│   ├── repository/
│   │   └── GameSessionRepository.java    (modify — 대상 통계 쿼리 추가)
│   └── dto/
│       └── TargetStatsResponse.java      (create)
├── user/
│   └── controller/
│       └── UserController.java           (modify — 대상 통계 엔드포인트 추가)

src/test/java/com/hwapulgi/api/
├── achievement/
│   └── service/
│       └── AchievementServiceTest.java
├── streak/
│   └── service/
│       └── StreakServiceTest.java
└── integration/
    ├── AchievementApiTest.java
    ├── StreakApiTest.java
    └── TargetStatsApiTest.java
```

---

## Task 1: Achievement — 엔티티, 타입, 리포지토리

**Files:**
- Create: `src/main/java/com/hwapulgi/api/achievement/entity/AchievementType.java`
- Create: `src/main/java/com/hwapulgi/api/achievement/entity/Achievement.java`
- Create: `src/main/java/com/hwapulgi/api/achievement/repository/AchievementRepository.java`
- Create: `src/main/java/com/hwapulgi/api/achievement/dto/AchievementResponse.java`

- [ ] **Step 1: Create AchievementType enum**

```java
package com.hwapulgi.api.achievement.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AchievementType {
    // 타격 수 기반
    HITS_100("백 번의 주먹", "누적 100회 타격 달성", 100),
    HITS_500("오백 번의 분노", "누적 500회 타격 달성", 500),
    HITS_1000("천 번의 해소", "누적 1,000회 타격 달성", 1000),

    // 세션 수 기반
    SESSIONS_10("열 번째 화풀기", "10회 화풀기 완료", 10),
    SESSIONS_50("화풀기 고수", "50회 화풀기 완료", 50),
    SESSIONS_100("화풀기 마스터", "100회 화풀기 완료", 100),

    // 해소율 기반
    RELEASE_80("마음이 편안", "해소율 80% 이상 달성", 80),
    RELEASE_90("거의 완벽", "해소율 90% 이상 달성", 90),
    RELEASE_100("완전 해소", "해소율 100% 달성", 100),

    // 스트릭 기반
    STREAK_3("3일 연속", "3일 연속 화풀기", 3),
    STREAK_7("일주일 내내", "7일 연속 화풀기", 7),
    STREAK_30("한 달 연속", "30일 연속 화풀기", 30),

    // 포인트 기반
    POINTS_500("500점 돌파", "한 세션에서 500점 달성", 500),
    POINTS_1000("천점 달인", "한 세션에서 1,000점 달성", 1000);

    private final String title;
    private final String description;
    private final int threshold;
}
```

- [ ] **Step 2: Create Achievement entity**

```java
package com.hwapulgi.api.achievement.entity;

import com.hwapulgi.api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "achievements", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "achievement_type"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type", nullable = false)
    private AchievementType achievementType;

    @CreationTimestamp
    private LocalDateTime achievedAt;

    public Achievement(User user, AchievementType achievementType) {
        this.user = user;
        this.achievementType = achievementType;
    }
}
```

- [ ] **Step 3: Create AchievementRepository**

```java
package com.hwapulgi.api.achievement.repository;

import com.hwapulgi.api.achievement.entity.Achievement;
import com.hwapulgi.api.achievement.entity.AchievementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByUserIdOrderByAchievedAtDesc(Long userId);
    boolean existsByUserIdAndAchievementType(Long userId, AchievementType type);
}
```

- [ ] **Step 4: Create AchievementResponse DTO**

```java
package com.hwapulgi.api.achievement.dto;

import com.hwapulgi.api.achievement.entity.Achievement;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AchievementResponse {
    private final String type;
    private final String title;
    private final String description;
    private final LocalDateTime achievedAt;

    public static AchievementResponse from(Achievement achievement) {
        return new AchievementResponse(
                achievement.getAchievementType().name(),
                achievement.getAchievementType().getTitle(),
                achievement.getAchievementType().getDescription(),
                achievement.getAchievedAt()
        );
    }
}
```

- [ ] **Step 5: Verify build**

Run: `./gradlew compileJava`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hwapulgi/api/achievement/
git commit -m "feat: add achievement entity, type enum, repository, DTO"
```

---

## Task 2: Achievement — 서비스 (세션 생성 시 자동 체크)

**Files:**
- Create: `src/main/java/com/hwapulgi/api/achievement/service/AchievementService.java`
- Modify: `src/main/java/com/hwapulgi/api/session/service/GameSessionService.java`
- Create: `src/test/java/com/hwapulgi/api/achievement/service/AchievementServiceTest.java`

- [ ] **Step 1: Create AchievementService**

```java
package com.hwapulgi.api.achievement.service;

import com.hwapulgi.api.achievement.dto.AchievementResponse;
import com.hwapulgi.api.achievement.entity.Achievement;
import com.hwapulgi.api.achievement.entity.AchievementType;
import com.hwapulgi.api.achievement.repository.AchievementRepository;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final GameSessionRepository gameSessionRepository;
    private final UserService userService;

    public List<AchievementResponse> getMyAchievements(Long userId) {
        return achievementRepository.findByUserIdOrderByAchievedAtDesc(userId).stream()
                .map(AchievementResponse::from)
                .toList();
    }

    @Transactional
    public List<AchievementResponse> checkAndAward(Long userId, int currentStreak) {
        User user = userService.findById(userId);
        List<GameSession> allSessions = gameSessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), null).getContent();

        int totalHits = allSessions.stream().mapToInt(GameSession::getHits).sum();
        int totalSessions = allSessions.size();
        GameSession latest = allSessions.isEmpty() ? null : allSessions.get(0);

        List<AchievementResponse> newAchievements = new ArrayList<>();

        // 타격 수
        checkAndAward(user, AchievementType.HITS_100, totalHits >= 100, newAchievements);
        checkAndAward(user, AchievementType.HITS_500, totalHits >= 500, newAchievements);
        checkAndAward(user, AchievementType.HITS_1000, totalHits >= 1000, newAchievements);

        // 세션 수
        checkAndAward(user, AchievementType.SESSIONS_10, totalSessions >= 10, newAchievements);
        checkAndAward(user, AchievementType.SESSIONS_50, totalSessions >= 50, newAchievements);
        checkAndAward(user, AchievementType.SESSIONS_100, totalSessions >= 100, newAchievements);

        // 해소율 (최신 세션 기준)
        if (latest != null) {
            int release = latest.getReleasedPercent();
            checkAndAward(user, AchievementType.RELEASE_80, release >= 80, newAchievements);
            checkAndAward(user, AchievementType.RELEASE_90, release >= 90, newAchievements);
            checkAndAward(user, AchievementType.RELEASE_100, release >= 100, newAchievements);

            // 포인트 (최신 세션 기준)
            int points = latest.getPoints();
            checkAndAward(user, AchievementType.POINTS_500, points >= 500, newAchievements);
            checkAndAward(user, AchievementType.POINTS_1000, points >= 1000, newAchievements);
        }

        // 스트릭
        checkAndAward(user, AchievementType.STREAK_3, currentStreak >= 3, newAchievements);
        checkAndAward(user, AchievementType.STREAK_7, currentStreak >= 7, newAchievements);
        checkAndAward(user, AchievementType.STREAK_30, currentStreak >= 30, newAchievements);

        return newAchievements;
    }

    private void checkAndAward(User user, AchievementType type, boolean condition,
                               List<AchievementResponse> newAchievements) {
        if (condition && !achievementRepository.existsByUserIdAndAchievementType(user.getId(), type)) {
            Achievement achievement = achievementRepository.save(new Achievement(user, type));
            newAchievements.add(AchievementResponse.from(achievement));
            log.info("Achievement unlocked: userId={}, type={}", user.getId(), type);
        }
    }
}
```

- [ ] **Step 2: Write AchievementServiceTest**

```java
package com.hwapulgi.api.achievement.service;

import com.hwapulgi.api.achievement.entity.Achievement;
import com.hwapulgi.api.achievement.entity.AchievementType;
import com.hwapulgi.api.achievement.dto.AchievementResponse;
import com.hwapulgi.api.achievement.repository.AchievementRepository;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock private AchievementRepository achievementRepository;
    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private UserService userService;

    @InjectMocks private AchievementService achievementService;

    @Test
    void checkAndAward_hitsThreshold_awardsAchievement() {
        User user = new User("1", "테스트");
        given(userService.findById(1L)).willReturn(user);

        GameSession session = GameSession.builder()
                .user(user).target("회사").targetNickname("상사")
                .angerBefore(100).angerAfter(10).hits(150).skillShots(0)
                .releasedPercent(90).points(200).build();

        given(gameSessionRepository.findByUserIdOrderByCreatedAtDesc(eq(null), any()))
                .willReturn(new PageImpl<>(List.of(session)));
        given(achievementRepository.existsByUserIdAndAchievementType(any(), any())).willReturn(false);
        given(achievementRepository.save(any(Achievement.class)))
                .willAnswer(inv -> inv.getArgument(0));

        List<AchievementResponse> results = achievementService.checkAndAward(1L, 0);

        assertThat(results).isNotEmpty();
        assertThat(results.stream().map(AchievementResponse::getType))
                .contains("HITS_100", "RELEASE_90", "RELEASE_80");
    }

    @Test
    void checkAndAward_alreadyHas_skips() {
        User user = new User("1", "테스트");
        given(userService.findById(1L)).willReturn(user);

        GameSession session = GameSession.builder()
                .user(user).target("회사").targetNickname("상사")
                .angerBefore(100).angerAfter(50).hits(150).skillShots(0)
                .releasedPercent(50).points(100).build();

        given(gameSessionRepository.findByUserIdOrderByCreatedAtDesc(eq(null), any()))
                .willReturn(new PageImpl<>(List.of(session)));
        given(achievementRepository.existsByUserIdAndAchievementType(any(), any())).willReturn(true);

        List<AchievementResponse> results = achievementService.checkAndAward(1L, 0);

        assertThat(results).isEmpty();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "com.hwapulgi.api.achievement.service.*"`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hwapulgi/api/achievement/service/ src/test/java/com/hwapulgi/api/achievement/
git commit -m "feat: add achievement service with auto-check logic and tests"
```

---

## Task 3: Streak — 서비스 (세션 날짜 기반 연속 플레이 계산)

**Files:**
- Create: `src/main/java/com/hwapulgi/api/streak/dto/StreakResponse.java`
- Create: `src/main/java/com/hwapulgi/api/streak/service/StreakService.java`
- Modify: `src/main/java/com/hwapulgi/api/session/repository/GameSessionRepository.java`
- Create: `src/test/java/com/hwapulgi/api/streak/service/StreakServiceTest.java`

- [ ] **Step 1: Add query to GameSessionRepository**

```java
// Add to GameSessionRepository.java
@Query("SELECT DISTINCT CAST(gs.createdAt AS LocalDate) FROM GameSession gs WHERE gs.user.id = :userId ORDER BY CAST(gs.createdAt AS LocalDate) DESC")
List<java.time.LocalDate> findDistinctPlayDatesByUserId(@Param("userId") Long userId);
```

- [ ] **Step 2: Create StreakResponse DTO**

```java
package com.hwapulgi.api.streak.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StreakResponse {
    private final int currentStreak;
    private final int bestStreak;
    private final int totalPlayDays;
}
```

- [ ] **Step 3: Create StreakService**

```java
package com.hwapulgi.api.streak.service;

import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.streak.dto.StreakResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreakService {

    private final GameSessionRepository gameSessionRepository;

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
        LocalDate today = LocalDate.now();
        LocalDate latest = sortedDatesDesc.get(0);

        // 오늘 또는 어제 플레이하지 않았으면 스트릭 0
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
```

- [ ] **Step 4: Write StreakServiceTest**

```java
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
        // 오늘 + 어제 (현재 2일), 그리고 5~8일 전 (과거 4일 연속)
        List<LocalDate> dates = List.of(
                today, today.minusDays(1),
                today.minusDays(5), today.minusDays(6), today.minusDays(7), today.minusDays(8));
        given(gameSessionRepository.findDistinctPlayDatesByUserId(1L)).willReturn(dates);

        StreakResponse result = streakService.getStreak(1L);

        assertThat(result.getCurrentStreak()).isEqualTo(2);
        assertThat(result.getBestStreak()).isEqualTo(4);
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "com.hwapulgi.api.streak.service.*"`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hwapulgi/api/streak/ src/main/java/com/hwapulgi/api/session/repository/ src/test/java/com/hwapulgi/api/streak/
git commit -m "feat: add streak service with current/best streak calculation and tests"
```

---

## Task 4: Target Stats — 대상별 통계 (쿼리 + DTO)

**Files:**
- Create: `src/main/java/com/hwapulgi/api/session/dto/TargetStatsResponse.java`
- Modify: `src/main/java/com/hwapulgi/api/session/repository/GameSessionRepository.java`
- Modify: `src/main/java/com/hwapulgi/api/session/service/GameSessionService.java`

- [ ] **Step 1: Create TargetStatsResponse DTO**

```java
package com.hwapulgi.api.session.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TargetStatsResponse {
    private final String target;
    private final long sessionCount;
    private final long totalHits;
    private final double avgRelease;
}
```

- [ ] **Step 2: Add aggregation query to GameSessionRepository**

Add to `GameSessionRepository.java`:

```java
@Query("SELECT new com.hwapulgi.api.session.dto.TargetStatsResponse(" +
       "gs.target, COUNT(gs), SUM(gs.hits), AVG(gs.releasedPercent)) " +
       "FROM GameSession gs WHERE gs.user.id = :userId " +
       "GROUP BY gs.target ORDER BY COUNT(gs) DESC")
List<com.hwapulgi.api.session.dto.TargetStatsResponse> findTargetStatsByUserId(@Param("userId") Long userId);
```

- [ ] **Step 3: Add getTargetStats to GameSessionService**

Add to `GameSessionService.java`:

```java
public List<TargetStatsResponse> getTargetStats(Long userId) {
    return gameSessionRepository.findTargetStatsByUserId(userId);
}
```

Add import: `import com.hwapulgi.api.session.dto.TargetStatsResponse;`

- [ ] **Step 4: Verify build**

Run: `./gradlew compileJava`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hwapulgi/api/session/
git commit -m "feat: add target stats aggregation query and DTO"
```

---

## Task 5: Controllers — API 엔드포인트 추가

**Files:**
- Create: `src/main/java/com/hwapulgi/api/achievement/controller/AchievementController.java`
- Create: `src/main/java/com/hwapulgi/api/streak/controller/StreakController.java`
- Modify: `src/main/java/com/hwapulgi/api/user/controller/UserController.java`
- Modify: `src/main/java/com/hwapulgi/api/session/service/GameSessionService.java` (세션 생성 시 업적+스트릭 체크)

- [ ] **Step 1: Create AchievementController**

```java
package com.hwapulgi.api.achievement.controller;

import com.hwapulgi.api.achievement.dto.AchievementResponse;
import com.hwapulgi.api.achievement.service.AchievementService;
import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final AuthService authService;
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<List<AchievementResponse>> getMyAchievements(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(achievementService.getMyAchievements(user.getId()));
    }
}
```

- [ ] **Step 2: Create StreakController**

```java
package com.hwapulgi.api.streak.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.streak.dto.StreakResponse;
import com.hwapulgi.api.streak.service.StreakService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/streaks")
@RequiredArgsConstructor
public class StreakController {

    private final StreakService streakService;
    private final AuthService authService;
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<StreakResponse> getMyStreak(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(streakService.getStreak(user.getId()));
    }
}
```

- [ ] **Step 3: Add target-stats endpoint to UserController**

Add to `UserController.java`:

```java
@GetMapping("/me/target-stats")
public ApiResponse<List<TargetStatsResponse>> getMyTargetStats(
        @RequestHeader(value = "Authorization", defaultValue = "") String token) {
    UserInfo userInfo = authService.authenticate(token);
    User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
    return ApiResponse.ok(gameSessionService.getTargetStats(user.getId()));
}
```

Add import: `import com.hwapulgi.api.session.dto.TargetStatsResponse;` and `import java.util.List;`

- [ ] **Step 4: Wire achievement + streak check into GameSessionService.createSession**

Add fields to `GameSessionService`:

```java
private final AchievementService achievementService;
private final StreakService streakService;
```

Add after ranking update in `createSession()`:

```java
try {
    int currentStreak = streakService.getCurrentStreak(userId);
    achievementService.checkAndAward(userId, currentStreak);
} catch (Exception e) {
    log.error("Failed to check achievements for userId={}: {}", userId, e.getMessage());
}
```

- [ ] **Step 5: Verify build**

Run: `./gradlew compileJava`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hwapulgi/api/
git commit -m "feat: add controllers for achievements, streaks, target stats"
```

---

## Task 6: Integration Tests

**Files:**
- Create: `src/test/java/com/hwapulgi/api/integration/AchievementApiTest.java`
- Create: `src/test/java/com/hwapulgi/api/integration/StreakApiTest.java`
- Create: `src/test/java/com/hwapulgi/api/integration/TargetStatsApiTest.java`

- [ ] **Step 1: Create AchievementApiTest**

```java
package com.hwapulgi.api.integration;

import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AchievementApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void getMyAchievements_empty() throws Exception {
        mockMvc.perform(get("/api/v1/achievements/me")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
```

- [ ] **Step 2: Create StreakApiTest**

```java
package com.hwapulgi.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StreakApiTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void getMyStreak_noSessions() throws Exception {
        mockMvc.perform(get("/api/v1/streaks/me")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreak").value(0))
                .andExpect(jsonPath("$.data.bestStreak").value(0))
                .andExpect(jsonPath("$.data.totalPlayDays").value(0));
    }
}
```

- [ ] **Step 3: Create TargetStatsApiTest**

```java
package com.hwapulgi.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TargetStatsApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private GameSessionRepository gameSessionRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        gameSessionRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        userRepository.save(new User("1", "테스트유저"));
    }

    @Test
    void getTargetStats_afterSessions() throws Exception {
        // 세션 2개 생성 (같은 대상)
        GameSessionCreateRequest req = new GameSessionCreateRequest(
                "회사", null, "상사", 80, 30, 50, 5, 62, 105, null);

        mockMvc.perform(post("/api/v1/sessions")
                .header("Authorization", "1:테스트유저")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(post("/api/v1/sessions")
                .header("Authorization", "1:테스트유저")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(get("/api/v1/users/me/target-stats")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].target").value("회사"))
                .andExpect(jsonPath("$.data[0].sessionCount").value(2));
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/hwapulgi/api/integration/
git commit -m "feat: add integration tests for achievements, streaks, target stats"
```

---

## Self-Review

- **업적 시스템:** 엔티티 + enum(14종) + 서비스(자동 체크) + 컨트롤러 + 테스트 ✓
- **스트릭:** 서비스(현재/최고/총 플레이일) + 컨트롤러 + 테스트 ✓
- **대상 통계:** JPQL 집계 쿼리 + DTO + 엔드포인트 + 테스트 ✓
- **연동:** 세션 생성 시 업적+스트릭 자동 체크 ✓
- **Placeholder scan:** 없음 ✓
- **Type consistency:** AchievementResponse, StreakResponse, TargetStatsResponse 일관 ✓
