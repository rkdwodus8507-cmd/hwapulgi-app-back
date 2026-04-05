# Hwapulgi Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 화풀기 앱 백엔드 — 사용자 인증, 게임 세션 CRUD, 랭킹/리더보드 API 구현

**Architecture:** 모놀리식 Spring Boot 3.5 앱. 도메인별 패키지 분리(auth, user, session, ranking, common). local 프로필은 H2 + Embedded Redis로 외부 의존 없이 개발/테스트 가능.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Spring Data Redis, H2, PostgreSQL, Lombok, JUnit 5

---

## File Structure

```
src/main/java/com/hwapulgi/api/
├── HwapulgiApplication.java                          (existing)
├── common/
│   ├── config/
│   │   └── EmbeddedRedisConfig.java                  (local 프로필 Embedded Redis)
│   ├── response/
│   │   ├── ApiResponse.java                          (공통 응답 래퍼)
│   │   └── ErrorResponse.java                        (에러 응답 DTO)
│   └── exception/
│       ├── ErrorCode.java                            (에러 코드 enum)
│       ├── BusinessException.java                    (커스텀 예외)
│       └── GlobalExceptionHandler.java               (@RestControllerAdvice)
├── auth/
│   ├── service/
│   │   ├── AuthService.java                          (인증 인터페이스)
│   │   └── DevAuthService.java                       (개발용 mock)
│   ├── controller/
│   │   └── AuthController.java                       (placeholder)
│   └── dto/
│       └── UserInfo.java                             (인증 결과 DTO)
├── user/
│   ├── entity/
│   │   └── User.java                                 (JPA 엔티티)
│   ├── repository/
│   │   └── UserRepository.java
│   ├── service/
│   │   └── UserService.java
│   ├── controller/
│   │   └── UserController.java
│   └── dto/
│       ├── UserProfileResponse.java
│       └── UserStatsResponse.java
├── session/
│   ├── entity/
│   │   └── GameSession.java                          (JPA 엔티티)
│   ├── repository/
│   │   └── GameSessionRepository.java
│   ├── service/
│   │   ├── GameSessionService.java
│   │   └── PointsCalculator.java                     (포인트 계산 유틸)
│   ├── controller/
│   │   └── GameSessionController.java
│   └── dto/
│       ├── GameSessionCreateRequest.java
│       ├── GameSessionResponse.java
│       └── AngerAfterUpdateRequest.java
└── ranking/
    ├── service/
    │   ├── RankingService.java                       (Redis 랭킹)
    │   └── RankingSnapshotScheduler.java             (@Scheduled)
    ├── entity/
    │   ├── RankingSnapshot.java                      (JPA 엔티티)
    │   └── PeriodType.java                           (enum)
    ├── repository/
    │   └── RankingSnapshotRepository.java
    ├── controller/
    │   └── RankingController.java
    └── dto/
        ├── RankingEntryResponse.java
        └── MyRankingResponse.java

src/main/resources/
├── application.properties                            (existing, 공통)
├── application-local.properties                      (H2 + Embedded Redis)
└── application-dev.properties                        (PostgreSQL + Redis)

src/test/java/com/hwapulgi/api/
├── HwapulgiApplicationTests.java                     (existing)
├── session/
│   └── service/
│       ├── PointsCalculatorTest.java
│       └── GameSessionServiceTest.java
├── ranking/
│   └── service/
│       └── RankingServiceTest.java
├── user/
│   └── service/
│       └── UserServiceTest.java
└── integration/
    ├── SessionApiTest.java
    ├── UserApiTest.java
    └── RankingApiTest.java
```

---

## Task 1: Common — 공통 응답 포맷 & 예외 처리

**Files:**
- Create: `src/main/java/com/hwapulgi/api/common/response/ApiResponse.java`
- Create: `src/main/java/com/hwapulgi/api/common/response/ErrorResponse.java`
- Create: `src/main/java/com/hwapulgi/api/common/exception/ErrorCode.java`
- Create: `src/main/java/com/hwapulgi/api/common/exception/BusinessException.java`
- Create: `src/main/java/com/hwapulgi/api/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create ApiResponse wrapper**

```java
// src/main/java/com/hwapulgi/api/common/response/ApiResponse.java
package com.hwapulgi.api.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final ErrorResponse error;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
```

- [ ] **Step 2: Create ErrorResponse DTO**

```java
// src/main/java/com/hwapulgi/api/common/response/ErrorResponse.java
package com.hwapulgi.api.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private final String code;
    private final String message;
}
```

- [ ] **Step 3: Create ErrorCode enum**

```java
// src/main/java/com/hwapulgi/api/common/exception/ErrorCode.java
package com.hwapulgi.api.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    // Session
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."),
    POINTS_MISMATCH(HttpStatus.BAD_REQUEST, "POINTS_MISMATCH", "포인트 검증에 실패했습니다."),

    // Ranking
    RANKING_NOT_FOUND(HttpStatus.NOT_FOUND, "RANKING_NOT_FOUND", "랭킹 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

- [ ] **Step 4: Create BusinessException**

```java
// src/main/java/com/hwapulgi/api/common/exception/BusinessException.java
package com.hwapulgi.api.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

- [ ] **Step 5: Create GlobalExceptionHandler**

```java
// src/main/java/com/hwapulgi/api/common/exception/GlobalExceptionHandler.java
package com.hwapulgi.api.common.exception;

import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.common.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(new ErrorResponse(code.getCode(), code.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("잘못된 입력입니다.");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(new ErrorResponse("INVALID_INPUT", message)));
    }
}
```

- [ ] **Step 6: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/hwapulgi/api/common/
git commit -m "feat: add common response format and exception handling"
```

---

## Task 2: Profiles & Configuration

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/application-local.properties`
- Create: `src/main/resources/application-dev.properties`

- [ ] **Step 1: Update application.properties (공통)**

```properties
# src/main/resources/application.properties
spring.application.name=hwapulgi
spring.profiles.active=local

# JPA common
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.format_sql=true
```

- [ ] **Step 2: Create application-local.properties**

```properties
# src/main/resources/application-local.properties
# H2 in-memory
spring.datasource.url=jdbc:h2:mem:hwapulgi;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true

# JPA
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true

# Redis (embedded — port 6370)
spring.data.redis.host=localhost
spring.data.redis.port=6370
```

- [ ] **Step 3: Create application-dev.properties**

```properties
# src/main/resources/application-dev.properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/hwapulgi
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=hwapulgi
spring.datasource.password=hwapulgi

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

- [ ] **Step 4: Add Embedded Redis dependency for test/local**

`build.gradle.kts` — add embedded redis dependency:

```kotlin
// add to dependencies block
implementation("it.ozimov:embedded-redis:0.7.3") { exclude(group = "org.slf4j") }
```

- [ ] **Step 5: Create EmbeddedRedisConfig**

```java
// src/main/java/com/hwapulgi/api/common/config/EmbeddedRedisConfig.java
package com.hwapulgi.api.common.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

@Configuration
@Profile("local")
public class EmbeddedRedisConfig {

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private RedisServer redisServer;

    @PostConstruct
    public void start() {
        redisServer = new RedisServer(redisPort);
        try {
            redisServer.start();
        } catch (Exception e) {
            // port already in use — skip (another test or app instance)
        }
    }

    @PreDestroy
    public void stop() {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }
}
```

- [ ] **Step 6: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts src/main/resources/ src/main/java/com/hwapulgi/api/common/config/
git commit -m "feat: add profile configs (local H2 + embedded Redis, dev PostgreSQL)"
```

---

## Task 3: Auth — 인증 인터페이스 & DevAuthService

**Files:**
- Create: `src/main/java/com/hwapulgi/api/auth/dto/UserInfo.java`
- Create: `src/main/java/com/hwapulgi/api/auth/service/AuthService.java`
- Create: `src/main/java/com/hwapulgi/api/auth/service/DevAuthService.java`

- [ ] **Step 1: Create UserInfo DTO**

```java
// src/main/java/com/hwapulgi/api/auth/dto/UserInfo.java
package com.hwapulgi.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserInfo {
    private final Long userId;
    private final String nickname;
}
```

- [ ] **Step 2: Create AuthService interface**

```java
// src/main/java/com/hwapulgi/api/auth/service/AuthService.java
package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.dto.UserInfo;

public interface AuthService {
    UserInfo authenticate(String token);
}
```

- [ ] **Step 3: Create DevAuthService**

개발용 mock — `X-USER-ID` 헤더 값을 userId로, `X-USER-NICKNAME` 헤더를 닉네임으로 사용. 헤더가 없으면 기본값 사용.

```java
// src/main/java/com/hwapulgi/api/auth/service/DevAuthService.java
package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.dto.UserInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "dev"})
public class DevAuthService implements AuthService {

    @Override
    public UserInfo authenticate(String token) {
        // token = "userId:nickname" format, or just "userId"
        if (token == null || token.isBlank()) {
            return new UserInfo(1L, "테스트유저");
        }
        String[] parts = token.split(":");
        Long userId = Long.parseLong(parts[0]);
        String nickname = parts.length > 1 ? parts[1] : "유저" + userId;
        return new UserInfo(userId, nickname);
    }
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hwapulgi/api/auth/
git commit -m "feat: add auth interface and dev mock implementation"
```

---

## Task 4: User — 엔티티, 리포지토리, 서비스

**Files:**
- Create: `src/main/java/com/hwapulgi/api/user/entity/User.java`
- Create: `src/main/java/com/hwapulgi/api/user/repository/UserRepository.java`
- Create: `src/main/java/com/hwapulgi/api/user/service/UserService.java`
- Create: `src/main/java/com/hwapulgi/api/user/dto/UserProfileResponse.java`
- Create: `src/main/java/com/hwapulgi/api/user/dto/UserStatsResponse.java`
- Test: `src/test/java/com/hwapulgi/api/user/service/UserServiceTest.java`

- [ ] **Step 1: Create User entity**

```java
// src/main/java/com/hwapulgi/api/user/entity/User.java
package com.hwapulgi.api.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String externalId;

    @Column(nullable = false)
    private String nickname;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public User(String externalId, String nickname) {
        this.externalId = externalId;
        this.nickname = nickname;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
```

- [ ] **Step 2: Create UserRepository**

```java
// src/main/java/com/hwapulgi/api/user/repository/UserRepository.java
package com.hwapulgi.api.user.repository;

import com.hwapulgi.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByExternalId(String externalId);
}
```

- [ ] **Step 3: Create DTOs**

```java
// src/main/java/com/hwapulgi/api/user/dto/UserProfileResponse.java
package com.hwapulgi.api.user.dto;

import com.hwapulgi.api.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserProfileResponse {
    private final Long id;
    private final String nickname;
    private final LocalDateTime createdAt;

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getCreatedAt());
    }
}
```

```java
// src/main/java/com/hwapulgi/api/user/dto/UserStatsResponse.java
package com.hwapulgi.api.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserStatsResponse {
    private final int totalSessions;
    private final int totalHits;
    private final int totalPoints;
    private final int bestRelease;
    private final int avgRelease;
}
```

- [ ] **Step 4: Create UserService**

```java
// src/main/java/com/hwapulgi/api/user/service/UserService.java
package com.hwapulgi.api.user.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.user.dto.UserProfileResponse;
import com.hwapulgi.api.user.dto.UserStatsResponse;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(Long externalUserId, String nickname) {
        String externalId = String.valueOf(externalUserId);
        return userRepository.findByExternalId(externalId)
                .orElseGet(() -> userRepository.save(new User(externalId, nickname)));
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = findById(userId);
        return UserProfileResponse.from(user);
    }
}
```

- [ ] **Step 5: Write UserService test**

```java
// src/test/java/com/hwapulgi/api/user/service/UserServiceTest.java
package com.hwapulgi.api.user.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getOrCreateUser_existingUser_returnsUser() {
        User existing = new User("1", "기존유저");
        given(userRepository.findByExternalId("1")).willReturn(Optional.of(existing));

        User result = userService.getOrCreateUser(1L, "기존유저");

        assertThat(result.getNickname()).isEqualTo("기존유저");
    }

    @Test
    void getOrCreateUser_newUser_createsAndReturns() {
        given(userRepository.findByExternalId("2")).willReturn(Optional.empty());
        User newUser = new User("2", "새유저");
        given(userRepository.save(any(User.class))).willReturn(newUser);

        User result = userService.getOrCreateUser(2L, "새유저");

        assertThat(result.getNickname()).isEqualTo("새유저");
    }

    @Test
    void findById_notFound_throwsBusinessException() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew test --tests "com.hwapulgi.api.user.service.UserServiceTest"`
Expected: 3 tests PASSED

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/hwapulgi/api/user/ src/test/java/com/hwapulgi/api/user/
git commit -m "feat: add user entity, repository, service with tests"
```

---

## Task 5: Session — 엔티티, 포인트 계산, 서비스

**Files:**
- Create: `src/main/java/com/hwapulgi/api/session/entity/GameSession.java`
- Create: `src/main/java/com/hwapulgi/api/session/repository/GameSessionRepository.java`
- Create: `src/main/java/com/hwapulgi/api/session/service/PointsCalculator.java`
- Create: `src/main/java/com/hwapulgi/api/session/dto/GameSessionCreateRequest.java`
- Create: `src/main/java/com/hwapulgi/api/session/dto/GameSessionResponse.java`
- Create: `src/main/java/com/hwapulgi/api/session/dto/AngerAfterUpdateRequest.java`
- Create: `src/main/java/com/hwapulgi/api/session/service/GameSessionService.java`
- Test: `src/test/java/com/hwapulgi/api/session/service/PointsCalculatorTest.java`
- Test: `src/test/java/com/hwapulgi/api/session/service/GameSessionServiceTest.java`

- [ ] **Step 1: Create PointsCalculator**

```java
// src/main/java/com/hwapulgi/api/session/service/PointsCalculator.java
package com.hwapulgi.api.session.service;

public class PointsCalculator {

    public static int calculate(int hits, int skillShots, int angerBefore, int angerAfter) {
        int effectiveAfter = Math.min(angerAfter, angerBefore);
        return 10 + hits + (skillShots * 4) + (angerBefore - effectiveAfter) / 2;
    }
}
```

- [ ] **Step 2: Write PointsCalculator test**

```java
// src/test/java/com/hwapulgi/api/session/service/PointsCalculatorTest.java
package com.hwapulgi.api.session.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointsCalculatorTest {

    @Test
    void calculate_normalCase() {
        // 10 + 50 + (5*4) + (80-30)/2 = 10 + 50 + 20 + 25 = 105
        assertThat(PointsCalculator.calculate(50, 5, 80, 30)).isEqualTo(105);
    }

    @Test
    void calculate_angerAfterHigherThanBefore_usesBeforeAsFloor() {
        // angerAfter(90) > angerBefore(50), so effectiveAfter = 50
        // 10 + 10 + (2*4) + (50-50)/2 = 10 + 10 + 8 + 0 = 28
        assertThat(PointsCalculator.calculate(10, 2, 50, 90)).isEqualTo(28);
    }

    @Test
    void calculate_zeroHitsAndSkills() {
        // 10 + 0 + 0 + (100-0)/2 = 10 + 50 = 60
        assertThat(PointsCalculator.calculate(0, 0, 100, 0)).isEqualTo(60);
    }

    @Test
    void calculate_allZero() {
        // 10 + 0 + 0 + 0 = 10
        assertThat(PointsCalculator.calculate(0, 0, 0, 0)).isEqualTo(10);
    }
}
```

- [ ] **Step 3: Run PointsCalculator test**

Run: `./gradlew test --tests "com.hwapulgi.api.session.service.PointsCalculatorTest"`
Expected: 4 tests PASSED

- [ ] **Step 4: Create GameSession entity**

```java
// src/main/java/com/hwapulgi/api/session/entity/GameSession.java
package com.hwapulgi.api.session.entity;

import com.hwapulgi.api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String target;

    private String customTarget;

    @Column(nullable = false)
    private String targetNickname;

    @Column(nullable = false)
    private int angerBefore;

    @Column(nullable = false)
    private int angerAfter;

    @Column(nullable = false)
    private int hits;

    @Column(nullable = false)
    private int skillShots;

    @Column(nullable = false)
    private int releasedPercent;

    @Column(nullable = false)
    private int points;

    private String memo;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public GameSession(User user, String target, String customTarget, String targetNickname,
                       int angerBefore, int angerAfter, int hits, int skillShots,
                       int releasedPercent, int points, String memo) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.target = target;
        this.customTarget = customTarget;
        this.targetNickname = targetNickname;
        this.angerBefore = angerBefore;
        this.angerAfter = angerAfter;
        this.hits = hits;
        this.skillShots = skillShots;
        this.releasedPercent = releasedPercent;
        this.points = points;
        this.memo = memo;
    }

    public void updateAngerAfter(int angerAfter) {
        this.angerAfter = angerAfter;
        if (this.angerBefore > 0) {
            this.releasedPercent = (int) (((double)(this.angerBefore - angerAfter) / this.angerBefore) * 100);
        }
    }
}
```

- [ ] **Step 5: Create GameSessionRepository**

```java
// src/main/java/com/hwapulgi/api/session/repository/GameSessionRepository.java
package com.hwapulgi.api.session.repository;

import com.hwapulgi.api.session.entity.GameSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
    Page<GameSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT gs FROM GameSession gs WHERE gs.user.id = :userId AND gs.createdAt >= :from")
    List<GameSession> findByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}
```

- [ ] **Step 6: Create DTOs**

```java
// src/main/java/com/hwapulgi/api/session/dto/GameSessionCreateRequest.java
package com.hwapulgi.api.session.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GameSessionCreateRequest {

    @NotBlank
    private String target;

    private String customTarget;

    @NotBlank
    private String targetNickname;

    @Min(0) @Max(100)
    private int angerBefore;

    @Min(0) @Max(100)
    private int angerAfter;

    @Min(0)
    private int hits;

    @Min(0)
    private int skillShots;

    @Min(0) @Max(100)
    private int releasedPercent;

    @Min(0)
    private int points;

    private String memo;
}
```

```java
// src/main/java/com/hwapulgi/api/session/dto/GameSessionResponse.java
package com.hwapulgi.api.session.dto;

import com.hwapulgi.api.session.entity.GameSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class GameSessionResponse {
    private final UUID id;
    private final String target;
    private final String customTarget;
    private final String targetNickname;
    private final int angerBefore;
    private final int angerAfter;
    private final int hits;
    private final int skillShots;
    private final int releasedPercent;
    private final int points;
    private final String memo;
    private final LocalDateTime createdAt;

    public static GameSessionResponse from(GameSession session) {
        return new GameSessionResponse(
                session.getId(), session.getTarget(), session.getCustomTarget(),
                session.getTargetNickname(), session.getAngerBefore(), session.getAngerAfter(),
                session.getHits(), session.getSkillShots(), session.getReleasedPercent(),
                session.getPoints(), session.getMemo(), session.getCreatedAt()
        );
    }
}
```

```java
// src/main/java/com/hwapulgi/api/session/dto/AngerAfterUpdateRequest.java
package com.hwapulgi.api.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AngerAfterUpdateRequest {
    @Min(0) @Max(100)
    private int angerAfter;
}
```

- [ ] **Step 7: Create GameSessionService**

```java
// src/main/java/com/hwapulgi/api/session/service/GameSessionService.java
package com.hwapulgi.api.session.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.dto.GameSessionResponse;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import com.hwapulgi.api.user.dto.UserStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;
    private final UserService userService;

    @Transactional
    public GameSessionResponse createSession(Long userId, GameSessionCreateRequest request) {
        User user = userService.findById(userId);

        int calculatedPoints = PointsCalculator.calculate(
                request.getHits(), request.getSkillShots(),
                request.getAngerBefore(), request.getAngerAfter());

        if (calculatedPoints != request.getPoints()) {
            throw new BusinessException(ErrorCode.POINTS_MISMATCH);
        }

        GameSession session = GameSession.builder()
                .user(user)
                .target(request.getTarget())
                .customTarget(request.getCustomTarget())
                .targetNickname(request.getTargetNickname())
                .angerBefore(request.getAngerBefore())
                .angerAfter(request.getAngerAfter())
                .hits(request.getHits())
                .skillShots(request.getSkillShots())
                .releasedPercent(request.getReleasedPercent())
                .points(calculatedPoints)
                .memo(request.getMemo())
                .build();

        return GameSessionResponse.from(gameSessionRepository.save(session));
    }

    public Page<GameSessionResponse> getMySessions(Long userId, Pageable pageable) {
        return gameSessionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(GameSessionResponse::from);
    }

    public GameSessionResponse getSession(UUID sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return GameSessionResponse.from(session);
    }

    @Transactional
    public GameSessionResponse updateAngerAfter(UUID sessionId, int angerAfter) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        session.updateAngerAfter(angerAfter);
        return GameSessionResponse.from(session);
    }

    public UserStatsResponse getUserStats(Long userId, LocalDateTime from) {
        List<GameSession> sessions = gameSessionRepository.findByUserIdAndCreatedAtAfter(userId, from);
        if (sessions.isEmpty()) {
            return new UserStatsResponse(0, 0, 0, 0, 0);
        }
        int totalSessions = sessions.size();
        int totalHits = sessions.stream().mapToInt(GameSession::getHits).sum();
        int totalPoints = sessions.stream().mapToInt(GameSession::getPoints).sum();
        int bestRelease = sessions.stream().mapToInt(GameSession::getReleasedPercent).max().orElse(0);
        int avgRelease = (int) sessions.stream().mapToInt(GameSession::getReleasedPercent).average().orElse(0);
        return new UserStatsResponse(totalSessions, totalHits, totalPoints, bestRelease, avgRelease);
    }
}
```

- [ ] **Step 8: Write GameSessionService test**

```java
// src/test/java/com/hwapulgi/api/session/service/GameSessionServiceTest.java
package com.hwapulgi.api.session.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.dto.GameSessionResponse;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private GameSessionService gameSessionService;

    @Test
    void createSession_validPoints_succeeds() {
        User user = new User("1", "테스트");
        given(userService.findById(1L)).willReturn(user);
        given(gameSessionRepository.save(any(GameSession.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // points = 10 + 50 + (5*4) + (80-30)/2 = 105
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사닉네임", 80, 30, 50, 5, 62, 105, null);

        GameSessionResponse response = gameSessionService.createSession(1L, request);

        assertThat(response.getPoints()).isEqualTo(105);
        assertThat(response.getTarget()).isEqualTo("회사");
    }

    @Test
    void createSession_invalidPoints_throwsException() {
        User user = new User("1", "테스트");
        given(userService.findById(1L)).willReturn(user);

        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사닉네임", 80, 30, 50, 5, 62, 9999, null);

        assertThatThrownBy(() -> gameSessionService.createSession(1L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getSession_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        given(gameSessionRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> gameSessionService.getSession(id))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 9: Run tests**

Run: `./gradlew test --tests "com.hwapulgi.api.session.service.*"`
Expected: 7 tests PASSED (4 PointsCalculator + 3 GameSessionService)

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/hwapulgi/api/session/ src/test/java/com/hwapulgi/api/session/
git commit -m "feat: add game session entity, points calculator, service with tests"
```

---

## Task 6: Ranking — Redis 서비스 & 스냅샷

**Files:**
- Create: `src/main/java/com/hwapulgi/api/ranking/entity/PeriodType.java`
- Create: `src/main/java/com/hwapulgi/api/ranking/entity/RankingSnapshot.java`
- Create: `src/main/java/com/hwapulgi/api/ranking/repository/RankingSnapshotRepository.java`
- Create: `src/main/java/com/hwapulgi/api/ranking/dto/RankingEntryResponse.java`
- Create: `src/main/java/com/hwapulgi/api/ranking/dto/MyRankingResponse.java`
- Create: `src/main/java/com/hwapulgi/api/ranking/service/RankingService.java`
- Create: `src/main/java/com/hwapulgi/api/ranking/service/RankingSnapshotScheduler.java`
- Test: `src/test/java/com/hwapulgi/api/ranking/service/RankingServiceTest.java`

- [ ] **Step 1: Create PeriodType enum**

```java
// src/main/java/com/hwapulgi/api/ranking/entity/PeriodType.java
package com.hwapulgi.api.ranking.entity;

public enum PeriodType {
    WEEKLY, MONTHLY, ALL_TIME
}
```

- [ ] **Step 2: Create RankingSnapshot entity**

```java
// src/main/java/com/hwapulgi/api/ranking/entity/RankingSnapshot.java
package com.hwapulgi.api.ranking.entity;

import com.hwapulgi.api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ranking_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PeriodType periodType;

    @Column(nullable = false)
    private String periodKey;

    private int totalPoints;
    private int totalSessions;
    private int totalHits;
    private int bestRelease;
    private int avgRelease;
    private int rank;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public RankingSnapshot(User user, PeriodType periodType, String periodKey,
                           int totalPoints, int totalSessions, int totalHits,
                           int bestRelease, int avgRelease, int rank) {
        this.user = user;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.totalPoints = totalPoints;
        this.totalSessions = totalSessions;
        this.totalHits = totalHits;
        this.bestRelease = bestRelease;
        this.avgRelease = avgRelease;
        this.rank = rank;
    }
}
```

- [ ] **Step 3: Create RankingSnapshotRepository**

```java
// src/main/java/com/hwapulgi/api/ranking/repository/RankingSnapshotRepository.java
package com.hwapulgi.api.ranking.repository;

import com.hwapulgi.api.ranking.entity.PeriodType;
import com.hwapulgi.api.ranking.entity.RankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {
    Optional<RankingSnapshot> findByUserIdAndPeriodTypeAndPeriodKey(Long userId, PeriodType periodType, String periodKey);
}
```

- [ ] **Step 4: Create DTOs**

```java
// src/main/java/com/hwapulgi/api/ranking/dto/RankingEntryResponse.java
package com.hwapulgi.api.ranking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RankingEntryResponse {
    private final int rank;
    private final Long userId;
    private final String nickname;
    private final double score;
}
```

```java
// src/main/java/com/hwapulgi/api/ranking/dto/MyRankingResponse.java
package com.hwapulgi.api.ranking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyRankingResponse {
    private final int rank;
    private final long totalParticipants;
    private final double score;
}
```

- [ ] **Step 5: Create RankingService**

```java
// src/main/java/com/hwapulgi/api/ranking/service/RankingService.java
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
        String weeklyKey = buildKey("release", "weekly", currentWeekKey());
        String monthlyKey = buildKey("release", "monthly", currentMonthKey());
        String allTimeKey = buildKey("release", "all_time", "all");

        ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
        String member = String.valueOf(userId);

        // best release — only update if higher
        Double current = ops.score(allTimeKey, member);
        if (current == null || releasePercent > current) {
            ops.add(weeklyKey, member, releasePercent);
            ops.add(monthlyKey, member, releasePercent);
            ops.add(allTimeKey, member, releasePercent);
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
```

- [ ] **Step 6: Create RankingSnapshotScheduler**

```java
// src/main/java/com/hwapulgi/api/ranking/service/RankingSnapshotScheduler.java
package com.hwapulgi.api.ranking.service;

import com.hwapulgi.api.ranking.entity.PeriodType;
import com.hwapulgi.api.ranking.entity.RankingSnapshot;
import com.hwapulgi.api.ranking.repository.RankingSnapshotRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSnapshotScheduler {

    private final StringRedisTemplate redisTemplate;
    private final RankingSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 0 * * MON") // 매주 월요일 00:00
    @Transactional
    public void snapshotWeekly() {
        String periodKey = RankingService.currentWeekKey();
        snapshot("points", "weekly", periodKey, PeriodType.WEEKLY);
        log.info("Weekly ranking snapshot saved: {}", periodKey);
    }

    @Scheduled(cron = "0 0 0 1 * *") // 매월 1일 00:00
    @Transactional
    public void snapshotMonthly() {
        String periodKey = RankingService.currentMonthKey();
        snapshot("points", "monthly", periodKey, PeriodType.MONTHLY);
        log.info("Monthly ranking snapshot saved: {}", periodKey);
    }

    private void snapshot(String criteria, String period, String periodKey, PeriodType periodType) {
        String key = "ranking:" + criteria + ":" + period + ":" + periodKey;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);

        if (tuples == null) return;

        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long userId = Long.parseLong(tuple.getValue());
            userRepository.findById(userId).ifPresent(user -> {
                RankingSnapshot snapshot = RankingSnapshot.builder()
                        .user(user)
                        .periodType(periodType)
                        .periodKey(periodKey)
                        .totalPoints(tuple.getScore().intValue())
                        .build();
                snapshotRepository.save(snapshot);
            });
            rank++;
        }
    }
}
```

- [ ] **Step 7: Write RankingService test**

```java
// src/test/java/com/hwapulgi/api/ranking/service/RankingServiceTest.java
package com.hwapulgi.api.ranking.service;

import com.hwapulgi.api.ranking.dto.MyRankingResponse;
import com.hwapulgi.api.ranking.dto.RankingEntryResponse;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private RankingService rankingService;
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @BeforeEach
    void setUp() {
        given(connectionFactory.getConnection()).willReturn(redisConnection);
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        rankingService = new RankingService(redisTemplate, userRepository);
    }

    @Test
    void currentWeekKey_returnsCorrectFormat() {
        String key = RankingService.currentWeekKey();
        assertThat(key).matches("\\d{4}-W\\d{2}");
    }

    @Test
    void currentMonthKey_returnsCorrectFormat() {
        String key = RankingService.currentMonthKey();
        assertThat(key).matches("\\d{4}-\\d{2}");
    }
}
```

- [ ] **Step 8: Run tests**

Run: `./gradlew test --tests "com.hwapulgi.api.ranking.service.*"`
Expected: 2 tests PASSED

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/hwapulgi/api/ranking/ src/test/java/com/hwapulgi/api/ranking/
git commit -m "feat: add ranking service with Redis sorted sets and snapshot scheduler"
```

---

## Task 7: Controllers — REST API 엔드포인트

**Files:**
- Create: `src/main/java/com/hwapulgi/api/session/controller/GameSessionController.java`
- Create: `src/main/java/com/hwapulgi/api/user/controller/UserController.java`
- Create: `src/main/java/com/hwapulgi/api/ranking/controller/RankingController.java`
- Create: `src/main/java/com/hwapulgi/api/auth/controller/AuthController.java`

- [ ] **Step 1: Create GameSessionController**

```java
// src/main/java/com/hwapulgi/api/session/controller/GameSessionController.java
package com.hwapulgi.api.session.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.ranking.service.RankingService;
import com.hwapulgi.api.session.dto.AngerAfterUpdateRequest;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.dto.GameSessionResponse;
import com.hwapulgi.api.session.service.GameSessionService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionService gameSessionService;
    private final RankingService rankingService;
    private final AuthService authService;
    private final UserService userService;

    @PostMapping
    public ApiResponse<GameSessionResponse> createSession(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @Valid @RequestBody GameSessionCreateRequest request) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());

        GameSessionResponse response = gameSessionService.createSession(user.getId(), request);

        rankingService.addPoints(user.getId(), response.getPoints());
        rankingService.updateReleaseRate(user.getId(), response.getReleasedPercent());

        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<Page<GameSessionResponse>> getMySessions(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @PageableDefault(size = 20) Pageable pageable) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(gameSessionService.getMySessions(user.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<GameSessionResponse> getSession(@PathVariable UUID id) {
        return ApiResponse.ok(gameSessionService.getSession(id));
    }

    @PatchMapping("/{id}/anger-after")
    public ApiResponse<GameSessionResponse> updateAngerAfter(
            @PathVariable UUID id,
            @Valid @RequestBody AngerAfterUpdateRequest request) {
        return ApiResponse.ok(gameSessionService.updateAngerAfter(id, request.getAngerAfter()));
    }
}
```

- [ ] **Step 2: Create UserController**

```java
// src/main/java/com/hwapulgi/api/user/controller/UserController.java
package com.hwapulgi.api.user.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.session.service.GameSessionService;
import com.hwapulgi.api.user.dto.UserProfileResponse;
import com.hwapulgi.api.user.dto.UserStatsResponse;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final GameSessionService gameSessionService;
    private final AuthService authService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(userService.getProfile(user.getId()));
    }

    @GetMapping("/me/stats")
    public ApiResponse<UserStatsResponse> getMyStats(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @RequestParam(defaultValue = "weekly") String period) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());

        LocalDateTime from = switch (period) {
            case "monthly" -> LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
            default -> LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        };

        return ApiResponse.ok(gameSessionService.getUserStats(user.getId(), from));
    }
}
```

- [ ] **Step 3: Create RankingController**

```java
// src/main/java/com/hwapulgi/api/ranking/controller/RankingController.java
package com.hwapulgi.api.ranking.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.ranking.dto.MyRankingResponse;
import com.hwapulgi.api.ranking.dto.RankingEntryResponse;
import com.hwapulgi.api.ranking.service.RankingService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final AuthService authService;
    private final UserService userService;

    @GetMapping("/points")
    public ApiResponse<List<RankingEntryResponse>> getPointsRanking(
            @RequestParam(defaultValue = "weekly") String period,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(rankingService.getTopRanking("points", period, limit));
    }

    @GetMapping("/release-rate")
    public ApiResponse<List<RankingEntryResponse>> getReleaseRateRanking(
            @RequestParam(defaultValue = "weekly") String period,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(rankingService.getTopRanking("release", period, limit));
    }

    @GetMapping("/me")
    public ApiResponse<MyRankingResponse> getMyRanking(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @RequestParam(defaultValue = "points") String criteria,
            @RequestParam(defaultValue = "weekly") String period) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(rankingService.getMyRanking(user.getId(), criteria, period));
    }
}
```

- [ ] **Step 4: Enable scheduling in HwapulgiApplication**

```java
// Modify: src/main/java/com/hwapulgi/api/HwapulgiApplication.java
package com.hwapulgi.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HwapulgiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HwapulgiApplication.class, args);
    }
}
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hwapulgi/api/
git commit -m "feat: add REST controllers for session, user, ranking APIs"
```

---

## Task 8: Integration Tests

**Files:**
- Create: `src/test/java/com/hwapulgi/api/integration/SessionApiTest.java`
- Create: `src/test/java/com/hwapulgi/api/integration/UserApiTest.java`
- Create: `src/test/java/com/hwapulgi/api/integration/RankingApiTest.java`

- [ ] **Step 1: Create SessionApiTest**

```java
// src/test/java/com/hwapulgi/api/integration/SessionApiTest.java
package com.hwapulgi.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SessionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new User("1", "테스트유저"));
    }

    @Test
    void createAndGetSession() throws Exception {
        // points = 10 + 50 + (5*4) + (80-30)/2 = 105
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사", 80, 30, 50, 5, 62, 105, "화풀기 완료");

        String sessionJson = mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "1:테스트유저")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.points").value(105))
                .andReturn().getResponse().getContentAsString();

        // Extract session ID and verify GET
        String sessionId = objectMapper.readTree(sessionJson).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target").value("회사"));
    }

    @Test
    void createSession_invalidPoints_returns400() throws Exception {
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사", 80, 30, 50, 5, 62, 9999, null);

        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "1:테스트유저")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

- [ ] **Step 2: Create UserApiTest**

```java
// src/test/java/com/hwapulgi/api/integration/UserApiTest.java
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
class UserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void getMyProfile_autoCreatesUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("테스트유저"));
    }

    @Test
    void getMyStats_emptyStats() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/stats")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSessions").value(0));
    }
}
```

- [ ] **Step 3: Create RankingApiTest**

```java
// src/test/java/com/hwapulgi/api/integration/RankingApiTest.java
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
class RankingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPointsRanking_emptyList() throws Exception {
        mockMvc.perform(get("/api/v1/rankings/points")
                        .param("period", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMyRanking_noData() throws Exception {
        mockMvc.perform(get("/api/v1/rankings/me")
                        .header("Authorization", "1:테스트유저"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").value(0));
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`
Expected: All tests PASSED

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/hwapulgi/api/integration/
git commit -m "feat: add integration tests for session, user, ranking APIs"
```

---

## Self-Review Checklist

- **Spec coverage:** Auth (interface + dev mock) ✓, User (profile + stats) ✓, Session (CRUD + points validation) ✓, Ranking (points + release-rate + my ranking) ✓, Common (response format + exception) ✓, Profiles (local/dev) ✓, Scheduler (weekly/monthly snapshot) ✓
  - Note: `/api/v1/rankings/streaks` (연속 플레이 랭킹) from spec is deferred — requires additional tracking logic not in the data model. Can be added as a follow-up.
- **Placeholder scan:** No TBD/TODO found. All steps have code.
- **Type consistency:** PointsCalculator.calculate() signature consistent across Task 5 steps. ApiResponse.ok()/error() used consistently in all controllers.