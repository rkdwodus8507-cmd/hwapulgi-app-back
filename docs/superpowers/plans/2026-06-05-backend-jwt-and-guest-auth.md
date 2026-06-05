# Backend JWT & Guest Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드에 디바이스 게스트 인증 엔드포인트와 자체 JWT 발급 시스템을 도입하고, 평문 `DevAuthService`를 제거하며, 기존 `TossAuthService`도 자체 JWT 발급 방식으로 전환한다.

**Architecture:** `AuthService` 인터페이스 구현체를 `DevAuthService`(평문 파싱) → `JwtAuthService`(HMAC SHA-256 검증)로 교체. 게스트는 `deviceId` 기반으로 `users` 테이블에 자동 가입·로그인하여 동일한 JWT를 발급받음. 토스 인증도 같은 JWT 발급 경로로 통합되어 추후 토스 로그인이 활성화돼도 컨트롤러 인증 코드를 일관되게 유지한다.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, jjwt 0.12.x, PostgreSQL(prod) / H2(test), JUnit 5, MockMvc.

**Spec:** `docs/superpowers/specs/2026-06-05-frontend-backend-integration-design.md`

**Scope:** 이 플랜은 본 스펙의 백엔드 변경(Section: Backend Changes 1~5)만 다룬다. 프론트엔드 변경은 본 플랜 머지 후 별도 플랜으로 진행한다.

---

## File Structure

### New files
- `src/main/java/com/hwapulgi/api/auth/jwt/JwtTokenProvider.java` — JWT 생성/검증 책임 한 곳
- `src/main/java/com/hwapulgi/api/auth/service/JwtAuthService.java` — `AuthService` 구현 (Bearer 토큰 → UserInfo)
- `src/main/java/com/hwapulgi/api/auth/service/GuestAuthService.java` — deviceId 기반 가입·로그인
- `src/main/java/com/hwapulgi/api/auth/controller/GuestAuthController.java` — `POST /api/auth/guest/login`
- `src/main/java/com/hwapulgi/api/auth/dto/GuestLoginRequest.java`
- `src/main/java/com/hwapulgi/api/auth/dto/GuestLoginResponse.java`
- `src/test/java/com/hwapulgi/api/auth/jwt/JwtTokenProviderTest.java`
- `src/test/java/com/hwapulgi/api/auth/service/JwtAuthServiceTest.java`
- `src/test/java/com/hwapulgi/api/auth/service/GuestAuthServiceTest.java`
- `src/test/java/com/hwapulgi/api/integration/GuestAuthApiTest.java`
- `src/test/java/com/hwapulgi/api/integration/support/AuthTokenFixture.java` — 통합 테스트에서 JWT 발급 헬퍼

### Modified files
- `build.gradle.kts` — `io.jsonwebtoken:jjwt-*` 의존성 추가
- `src/main/java/com/hwapulgi/api/user/entity/User.java` — `deviceId` 컬럼 + 게스트 생성자
- `src/main/java/com/hwapulgi/api/user/repository/UserRepository.java` — `findByDeviceId`
- `src/main/java/com/hwapulgi/api/auth/service/TossAuthService.java` — 자체 JWT 발급으로 전환
- `src/main/java/com/hwapulgi/api/auth/dto/TossLoginResponse.java` — 토스 토큰 → 자체 JWT로 의미 변경
- `src/main/java/com/hwapulgi/api/auth/controller/TossAuthController.java` — `unlink`가 JWT에서 userId 추출
- `src/main/resources/application.properties` — `jwt.secret`, `jwt.access-token-expiry-seconds`, `jwt.refresh-token-expiry-seconds`
- `src/main/java/com/hwapulgi/api/common/config/SwaggerConfig.java` — 인증 설명 갱신
- 8개 통합 테스트 (`SessionApiTest`, `UserApiTest`, `HomeApiTest`, `ReportApiTest`, `RankingApiTest`, `AchievementApiTest`, `StreakApiTest`, `TargetStatsApiTest`) — 평문 토큰 → JWT 헬퍼 사용

### Deleted files
- `src/main/java/com/hwapulgi/api/auth/service/DevAuthService.java`

---

## Task 1: jjwt 의존성 추가

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: 의존성 추가**

`build.gradle.kts` `dependencies` 블록에 추가:
```kotlin
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

- [ ] **Step 2: 빌드 검증**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL (jjwt 다운로드 후 컴파일 성공)

- [ ] **Step 3: 커밋**

```bash
git add build.gradle.kts
git commit -m "chore: jjwt 의존성 추가"
```

---

## Task 2: `User` 엔티티에 `deviceId` 컬럼 추가

**Files:**
- Modify: `src/main/java/com/hwapulgi/api/user/entity/User.java`
- Modify: `src/main/java/com/hwapulgi/api/user/repository/UserRepository.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/hwapulgi/api/user/repository/UserRepositoryDeviceIdTest.java` 신규:
```java
@SpringBootTest
@ActiveProfiles("local")
class UserRepositoryDeviceIdTest {
    @Autowired UserRepository userRepository;

    @AfterEach
    void cleanUp() { userRepository.deleteAll(); }

    @Test
    void findByDeviceId_returns_user_when_device_matches() {
        userRepository.save(User.guest("device-uuid-1", "게스트"));
        assertThat(userRepository.findByDeviceId("device-uuid-1")).isPresent();
    }

    @Test
    void findByDeviceId_returns_empty_when_not_found() {
        assertThat(userRepository.findByDeviceId("nope")).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests UserRepositoryDeviceIdTest`
Expected: 컴파일 실패 — `User.guest`, `findByDeviceId` 미존재

- [ ] **Step 3: 엔티티 변경**

`User.java`:
- `externalId`를 `nullable = true`로 변경
- `@Column(unique = true, nullable = true) private String deviceId;` 추가
- 정적 팩토리 추가:
```java
public static User guest(String deviceId, String nickname) {
    User u = new User();
    u.deviceId = deviceId;
    u.nickname = nickname;
    return u;
}
public static User tossUser(String externalId, String nickname) {
    User u = new User();
    u.externalId = externalId;
    u.nickname = nickname;
    return u;
}
```
- 기존 `public User(String externalId, String nickname)` 생성자는 `@Deprecated` 표시 후 유지 (마이그레이션용, 후속 task에서 제거)

- [ ] **Step 4: Repository 메서드 추가**

`UserRepository.java`에 추가:
```java
Optional<User> findByDeviceId(String deviceId);
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests UserRepositoryDeviceIdTest`
Expected: 두 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/hwapulgi/api/user/ src/test/java/com/hwapulgi/api/user/repository/UserRepositoryDeviceIdTest.java
git commit -m "feat: User 엔티티에 deviceId 컬럼과 게스트 팩토리 추가"
```

---

## Task 3: `JwtTokenProvider` 구현

**Files:**
- Create: `src/main/java/com/hwapulgi/api/auth/jwt/JwtTokenProvider.java`
- Create: `src/test/java/com/hwapulgi/api/auth/jwt/JwtTokenProviderTest.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: 실패하는 테스트 작성**

`JwtTokenProviderTest.java`:
```java
class JwtTokenProviderTest {
    private final JwtTokenProvider provider = new JwtTokenProvider(
        "0123456789abcdef0123456789abcdef", 3600L, 86_400L * 30, Clock.systemUTC()
    );

    @Test
    void createAccessToken_then_parse_returns_userId_and_nickname() {
        String token = provider.createAccessToken(42L, "테스트유저");
        JwtPayload payload = provider.parseAccessToken(token);
        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.nickname()).isEqualTo("테스트유저");
    }

    @Test
    void parseAccessToken_throws_when_signature_invalid() {
        JwtTokenProvider other = new JwtTokenProvider(
            "different-secret-different-secret", 3600L, 86_400L * 30, Clock.systemUTC()
        );
        String tampered = other.createAccessToken(42L, "x");
        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void parseAccessToken_throws_when_expired() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider shortLived = new JwtTokenProvider(
            "0123456789abcdef0123456789abcdef", 1L, 1L, fixed
        );
        String token = shortLived.createAccessToken(1L, "x");
        Clock later = Clock.fixed(Instant.parse("2026-01-01T00:00:10Z"), ZoneOffset.UTC);
        JwtTokenProvider laterProvider = new JwtTokenProvider(
            "0123456789abcdef0123456789abcdef", 1L, 1L, later
        );
        assertThatThrownBy(() -> laterProvider.parseAccessToken(token))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void refreshToken_does_not_contain_nickname_claim() {
        String refresh = provider.createRefreshToken(42L);
        JwtRefreshPayload payload = provider.parseRefreshToken(refresh);
        assertThat(payload.userId()).isEqualTo(42L);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests JwtTokenProviderTest`
Expected: 컴파일 실패

- [ ] **Step 3: `JwtTokenProvider` 구현**

`JwtTokenProvider.java`:
- 생성자 4파라미터: `String secret`, `long accessExpirySeconds`, `long refreshExpirySeconds`, `Clock clock`
- secret은 `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`로 SecretKey 생성 (최소 32바이트)
- `createAccessToken(userId, nickname)`: `subject = userId.toString()`, claim `nickname`, `iat`/`exp` from `clock`, `type=access`
- `createRefreshToken(userId)`: `subject`, claim `type=refresh`, `iat`/`exp`
- `parseAccessToken(token)`: `Jwts.parser().verifyWith(key).clock(...).build().parseSignedClaims(...)` — `SignatureException`/`ExpiredJwtException` → `throw new BusinessException(ErrorCode.UNAUTHORIZED)`. `type` claim이 `access`가 아니면 동일 예외.
- `parseRefreshToken(token)`: 동일하되 `type=refresh` 검증
- `JwtPayload(long userId, String nickname)`, `JwtRefreshPayload(long userId)` record로 정의 (같은 파일)

Spring 주입을 위해 `@Component` + `@Value` 사용한 보조 생성자 추가:
```java
@Component
public class JwtTokenProvider {
    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-expiry-seconds}") long accessExpirySeconds,
        @Value("${jwt.refresh-token-expiry-seconds}") long refreshExpirySeconds
    ) {
        this(secret, accessExpirySeconds, refreshExpirySeconds, Clock.systemUTC());
    }
    // package-private 4-arg 생성자 (테스트용)
    JwtTokenProvider(String secret, long accessExpirySeconds, long refreshExpirySeconds, Clock clock) { ... }
}
```

- [ ] **Step 4: properties 추가**

`application.properties`:
```properties
jwt.secret=${JWT_SECRET:dev-only-secret-please-replace-me-32-bytes-min}
jwt.access-token-expiry-seconds=3600
jwt.refresh-token-expiry-seconds=2592000
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests JwtTokenProviderTest`
Expected: 4개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/hwapulgi/api/auth/jwt/ src/test/java/com/hwapulgi/api/auth/jwt/ src/main/resources/application.properties
git commit -m "feat: JwtTokenProvider 추가 (HMAC SHA-256 access/refresh)"
```

---

## Task 4: `JwtAuthService` 구현 (`AuthService` 신규 구현체)

**Files:**
- Create: `src/main/java/com/hwapulgi/api/auth/service/JwtAuthService.java`
- Create: `src/test/java/com/hwapulgi/api/auth/service/JwtAuthServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`JwtAuthServiceTest.java`:
```java
class JwtAuthServiceTest {
    private final JwtTokenProvider provider = new JwtTokenProvider(
        "0123456789abcdef0123456789abcdef", 3600L, 86_400L * 30, Clock.systemUTC()
    );
    private final JwtAuthService service = new JwtAuthService(provider);

    @Test
    void authenticate_returns_userInfo_when_bearer_token_valid() {
        String token = provider.createAccessToken(7L, "닉");
        UserInfo info = service.authenticate("Bearer " + token);
        assertThat(info.userId()).isEqualTo(7L);
        assertThat(info.nickname()).isEqualTo("닉");
    }

    @Test
    void authenticate_throws_when_header_missing_bearer_prefix() {
        String token = provider.createAccessToken(7L, "닉");
        assertThatThrownBy(() -> service.authenticate(token))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void authenticate_throws_when_token_blank() {
        assertThatThrownBy(() -> service.authenticate(""))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.authenticate("Bearer "))
            .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests JwtAuthServiceTest`
Expected: 컴파일 실패

- [ ] **Step 3: 구현**

`JwtAuthService.java`:
```java
@Service
@Profile({"local", "dev", "prod"})
@RequiredArgsConstructor
public class JwtAuthService implements AuthService {
    private final JwtTokenProvider tokenProvider;

    @Override
    public UserInfo authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        JwtPayload payload = tokenProvider.parseAccessToken(token);
        return new UserInfo(payload.userId(), payload.nickname());
    }
}
```

**중요:** 이 시점에서 `DevAuthService`와 `JwtAuthService`가 **두 개 모두 같은 `AuthService` 빈으로 등록**되어 컨텍스트 로드 실패함. Task 6에서 `DevAuthService` 제거하면서 해소. 그 전에는 `@Primary`로 임시 우회.

`JwtAuthService` 클래스 위에 `@Primary` 추가 (Task 6에서 제거).

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests JwtAuthServiceTest`
Expected: 3개 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/hwapulgi/api/auth/service/JwtAuthService.java src/test/java/com/hwapulgi/api/auth/service/JwtAuthServiceTest.java
git commit -m "feat: JwtAuthService 추가 (Bearer 토큰 검증)"
```

---

## Task 5: `GuestAuthService` 구현

**Files:**
- Create: `src/main/java/com/hwapulgi/api/auth/service/GuestAuthService.java`
- Create: `src/test/java/com/hwapulgi/api/auth/service/GuestAuthServiceTest.java`
- Create: `src/main/java/com/hwapulgi/api/auth/dto/GuestLoginResponse.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`GuestAuthServiceTest.java`:
```java
@SpringBootTest
@ActiveProfiles("local")
class GuestAuthServiceTest {
    @Autowired GuestAuthService service;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider tokenProvider;

    @AfterEach void cleanUp() { userRepository.deleteAll(); }

    @Test
    void login_creates_new_user_when_deviceId_unseen() {
        GuestLoginResponse res = service.login("uuid-new");
        assertThat(userRepository.findByDeviceId("uuid-new")).isPresent();
        assertThat(res.nickname()).isEqualTo("게스트" + res.userId());
        assertThat(tokenProvider.parseAccessToken(res.accessToken()).userId())
            .isEqualTo(res.userId());
    }

    @Test
    void login_returns_existing_user_when_deviceId_known() {
        GuestLoginResponse first = service.login("uuid-known");
        GuestLoginResponse second = service.login("uuid-known");
        assertThat(second.userId()).isEqualTo(first.userId());
    }

    @Test
    void login_throws_when_deviceId_blank() {
        assertThatThrownBy(() -> service.login(""))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.login("   "))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.login(null))
            .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests GuestAuthServiceTest`
Expected: 컴파일 실패

- [ ] **Step 3: DTO 작성**

`GuestLoginResponse.java`:
```java
public record GuestLoginResponse(
    Long userId,
    String nickname,
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
```

- [ ] **Step 4: 서비스 구현**

`GuestAuthService.java`:
```java
@Service
@RequiredArgsConstructor
public class GuestAuthService {
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    @Value("${jwt.access-token-expiry-seconds}") private long accessExpiry;

    @Transactional
    public GuestLoginResponse login(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        User user = userRepository.findByDeviceId(deviceId)
            .orElseGet(() -> {
                User saved = userRepository.save(User.guest(deviceId, "temp"));
                saved.updateNickname("게스트" + saved.getId());
                return saved;
            });
        return new GuestLoginResponse(
            user.getId(),
            user.getNickname(),
            tokenProvider.createAccessToken(user.getId(), user.getNickname()),
            tokenProvider.createRefreshToken(user.getId()),
            accessExpiry
        );
    }
}
```

**Note on nickname assignment:** 신규 가입 시 `userId`가 필요하지만 `userId`는 save 후에 생성됨. 그래서 임시 닉네임으로 저장 → ID 받기 → updateNickname. 이 순서는 트랜잭션 내라 한 번의 flush로 처리됨.

`ErrorCode.INVALID_INPUT`이 없으면 추가 (이미 있을 가능성 높음 — 확인 후 없으면 enum에 추가):
```java
INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다");
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests GuestAuthServiceTest`
Expected: 3개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/hwapulgi/api/auth/service/GuestAuthService.java src/main/java/com/hwapulgi/api/auth/dto/GuestLoginResponse.java src/test/java/com/hwapulgi/api/auth/service/GuestAuthServiceTest.java
git commit -m "feat: GuestAuthService 추가 (deviceId 기반 가입/로그인)"
```

---

## Task 6: `GuestAuthController` 신설 + 통합 테스트

**Files:**
- Create: `src/main/java/com/hwapulgi/api/auth/controller/GuestAuthController.java`
- Create: `src/main/java/com/hwapulgi/api/auth/dto/GuestLoginRequest.java`
- Create: `src/test/java/com/hwapulgi/api/integration/GuestAuthApiTest.java`

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`GuestAuthApiTest.java`:
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class GuestAuthApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;

    @BeforeEach void cleanUp() { userRepository.deleteAll(); }

    @Test
    void POST_guest_login_creates_user_and_returns_tokens() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("deviceId", "uuid-1"));
        mockMvc.perform(post("/api/auth/guest/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").isNumber())
            .andExpect(jsonPath("$.data.nickname").value(org.hamcrest.Matchers.startsWith("게스트")))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.refreshToken").isString())
            .andExpect(jsonPath("$.data.expiresIn").isNumber());
    }

    @Test
    void POST_guest_login_returns_400_when_deviceId_missing() throws Exception {
        mockMvc.perform(post("/api/auth/guest/login")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests GuestAuthApiTest`
Expected: 404 — 엔드포인트 없음

- [ ] **Step 3: DTO + 컨트롤러 구현**

`GuestLoginRequest.java`:
```java
public record GuestLoginRequest(@NotBlank String deviceId) {}
```

`GuestAuthController.java`:
```java
@RestController
@RequestMapping("/api/auth/guest")
@RequiredArgsConstructor
public class GuestAuthController {
    private final GuestAuthService guestAuthService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<GuestLoginResponse>> login(
            @Valid @RequestBody GuestLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(guestAuthService.login(request.deviceId())));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests GuestAuthApiTest`
Expected: 2개 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/hwapulgi/api/auth/controller/GuestAuthController.java src/main/java/com/hwapulgi/api/auth/dto/GuestLoginRequest.java src/test/java/com/hwapulgi/api/integration/GuestAuthApiTest.java
git commit -m "feat: POST /api/auth/guest/login 엔드포인트 추가"
```

---

## Task 7: 통합 테스트 JWT 헬퍼 작성

**Files:**
- Create: `src/test/java/com/hwapulgi/api/integration/support/AuthTokenFixture.java`

- [ ] **Step 1: 헬퍼 작성**

`AuthTokenFixture.java`:
```java
@TestComponent
@RequiredArgsConstructor
public class AuthTokenFixture {
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    /**
     * 통합 테스트에서 사용자를 생성하고 그 사용자에 대한 Bearer 헤더 값을 만든다.
     * 기존 평문 토큰 `"1:테스트유저"`을 대체한다.
     */
    public String bearerForUser(User user) {
        return "Bearer " + tokenProvider.createAccessToken(user.getId(), user.getNickname());
    }
}
```

`@TestComponent`는 명시적 import 필요 시 `@Import(AuthTokenFixture.class)`를 각 통합 테스트 클래스에 추가하여 사용.

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/hwapulgi/api/integration/support/AuthTokenFixture.java
git commit -m "test: 통합 테스트용 JWT 헬퍼 AuthTokenFixture 추가"
```

---

## Task 8: `DevAuthService` 제거 + 기존 통합 테스트 8개 JWT 헬퍼로 마이그레이션

**Files:**
- Delete: `src/main/java/com/hwapulgi/api/auth/service/DevAuthService.java`
- Modify: `src/main/java/com/hwapulgi/api/auth/service/JwtAuthService.java` — `@Primary` 제거
- Modify: `src/main/java/com/hwapulgi/api/common/config/SwaggerConfig.java`
- Modify: 8개 통합 테스트:
  - `SessionApiTest.java`
  - `UserApiTest.java`
  - `HomeApiTest.java`
  - `ReportApiTest.java`
  - `RankingApiTest.java`
  - `AchievementApiTest.java`
  - `StreakApiTest.java`
  - `TargetStatsApiTest.java`

- [ ] **Step 1: 현재 평문 토큰 사용처 확인**

Run: `grep -rn '"1:테스트유저"\|"1:' src/test --include='*.java'`
Expected: 통합 테스트 8개에서 `Authorization` 헤더로 평문 토큰 사용 중인 것 출력

- [ ] **Step 2: `DevAuthService` 삭제**

Run: `rm src/main/java/com/hwapulgi/api/auth/service/DevAuthService.java`

- [ ] **Step 3: `JwtAuthService`의 `@Primary` 제거**

`JwtAuthService.java`에서 `@Primary` 어노테이션 삭제 (단일 빈만 남았으므로 불필요).

- [ ] **Step 4: 통합 테스트 8개 마이그레이션 (각 파일 동일 패턴)**

각 통합 테스트 파일에 다음 변경:

(a) 클래스에 `@Import(AuthTokenFixture.class)` 추가, `AuthTokenFixture` 주입:
```java
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local")
@Import(AuthTokenFixture.class)
class SessionApiTest {
    @Autowired AuthTokenFixture authTokens;
    ...
}
```

(b) `@BeforeEach`에서 생성한 테스트 유저를 필드에 보관:
```java
private User testUser;

@BeforeEach
void setUp() {
    ...
    testUser = userRepository.save(User.tossUser("1", "테스트유저"));
}
```

(c) 모든 `.header("Authorization", "1:테스트유저")` → `.header("Authorization", authTokens.bearerForUser(testUser))`로 치환.

- [ ] **Step 5: SwaggerConfig 인증 설명 갱신**

`SwaggerConfig.java`의 `"인증: Authorization 헤더에 \`userId:nickname\` 형식..."` 텍스트를:
```
"인증: Authorization 헤더에 `Bearer <JWT>` 형식으로 전달. JWT는 `POST /api/auth/guest/login` 또는 토스 로그인으로 발급."
```

- [ ] **Step 6: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 모든 테스트 PASS (8개 통합 테스트 포함)

- [ ] **Step 7: 커밋**

```bash
git add -u
git commit -m "refactor: DevAuthService 제거, 통합 테스트를 JWT 헬퍼로 전환"
```

---

## Task 9: `TossAuthService.login()` — 자체 JWT 발급으로 전환

**Files:**
- Modify: `src/main/java/com/hwapulgi/api/auth/service/TossAuthService.java`
- Modify: `src/main/java/com/hwapulgi/api/auth/dto/TossLoginResponse.java` (필드 의미 변경, 구조 동일)

- [ ] **Step 1: 기존 `TossAuthService` 테스트 상태 확인**

Run: `find src/test -name 'TossAuthServiceTest.java'`
Expected: 없음 (없다면 신규 작성)

`src/test/java/com/hwapulgi/api/auth/service/TossAuthServiceTest.java` 신규:
```java
@SpringBootTest
@ActiveProfiles("local")
class TossAuthServiceTest {
    @Autowired TossAuthService tossAuthService;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @MockBean AppsInTossClient appsInTossClient;

    @AfterEach void cleanUp() { userRepository.deleteAll(); }

    @Test
    void login_returns_self_issued_jwt_not_toss_access_token() {
        given(appsInTossClient.generateToken(any(), any())).willReturn(/* 성공 응답, accessToken=TOSS_AT */);
        given(appsInTossClient.getLoginMe("TOSS_AT")).willReturn(/* 성공 응답, userKey=12345 */);

        TossLoginResponse res = tossAuthService.login(new TossLoginRequest("code", "ref"));

        assertThat(res.accessToken()).isNotEqualTo("TOSS_AT");
        JwtPayload payload = tokenProvider.parseAccessToken(res.accessToken());
        assertThat(payload.userId()).isEqualTo(res.userId());
    }
}
```

(빌더 또는 record 생성자에 맞춰 응답 객체 구성 — `TossGenerateTokenResponse` 구조 확인 후 작성.)

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests TossAuthServiceTest`
Expected: 실패 — 현재 코드는 토스 accessToken을 그대로 반환

- [ ] **Step 3: `TossAuthService.login()` 변경**

`TossAuthService.java`에 `JwtTokenProvider` 주입 + `accessExpiry` 주입.

`login()` 마지막 return 부분 변경:
```java
String selfAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getNickname());
String selfRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());
return new TossLoginResponse(
    user.getId(), user.getNickname(),
    selfAccessToken, selfRefreshToken,
    accessExpiry
);
```

기존 `User` 생성자 호출은 `User.tossUser(externalId, nickname)`으로 변경 (Task 2의 팩토리 사용).

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests TossAuthServiceTest`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/hwapulgi/api/auth/service/TossAuthService.java src/test/java/com/hwapulgi/api/auth/service/TossAuthServiceTest.java
git commit -m "refactor: TossAuthService.login()이 자체 JWT 발급하도록 전환"
```

---

## Task 10: `TossAuthService.refresh()` — 자체 JWT 갱신으로 전환

**Files:**
- Modify: `src/main/java/com/hwapulgi/api/auth/service/TossAuthService.java`

**핵심 변화:** 현재 `refresh()`는 클라가 보낸 **토스 refresh token**으로 토스 API 호출. 변경 후 클라가 보내는 건 **자체 refresh JWT**. 토스 API는 더 이상 호출하지 않음.

- [ ] **Step 1: 실패하는 테스트 추가**

`TossAuthServiceTest.java`에 추가:
```java
@Test
void refresh_validates_self_refresh_jwt_and_reissues_access_token() {
    User user = userRepository.save(User.tossUser("toss-123", "토스유저"));
    String refresh = tokenProvider.createRefreshToken(user.getId());

    TossLoginResponse res = tossAuthService.refresh(refresh);

    assertThat(res.userId()).isEqualTo(user.getId());
    assertThat(tokenProvider.parseAccessToken(res.accessToken()).userId())
        .isEqualTo(user.getId());
    // appsInTossClient.refreshToken 호출 없음 검증
    then(appsInTossClient).should(never()).refreshToken(any());
}

@Test
void refresh_throws_when_refresh_jwt_invalid() {
    assertThatThrownBy(() -> tossAuthService.refresh("not-a-jwt"))
        .isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests TossAuthServiceTest`
Expected: 실패

- [ ] **Step 3: `refresh()` 재구현**

```java
public TossLoginResponse refresh(String refreshToken) {
    JwtRefreshPayload payload = jwtTokenProvider.parseRefreshToken(refreshToken);
    User user = userRepository.findById(payload.userId())
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    return new TossLoginResponse(
        user.getId(), user.getNickname(),
        jwtTokenProvider.createAccessToken(user.getId(), user.getNickname()),
        jwtTokenProvider.createRefreshToken(user.getId()),
        accessExpiry
    );
}
```

토스 클라이언트 호출 코드 모두 제거.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests TossAuthServiceTest`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/hwapulgi/api/auth/service/TossAuthService.java src/test/java/com/hwapulgi/api/auth/service/TossAuthServiceTest.java
git commit -m "refactor: TossAuthService.refresh()가 자체 refresh JWT를 검증하도록 전환"
```

---

## Task 11: `TossAuthService.unlink()` — JWT에서 userId 추출

**Files:**
- Modify: `src/main/java/com/hwapulgi/api/auth/service/TossAuthService.java`
- Modify: `src/main/java/com/hwapulgi/api/auth/controller/TossAuthController.java`

**현재:** `unlink()`는 `accessToken`(토스 토큰 가정)으로 `appsInTossClient.removeByAccessToken()` 호출.
**변경:** 자체 JWT에서 userId 추출 → `externalId`(=tossUserKey) 조회 후, 우리 DB에서 user 삭제(또는 비활성화 정책)만 수행. 토스 측 연결 해제는 별도 콜백(`callback/unlink`)으로 처리되므로 우리 쪽 정리만 신경.

> **Note:** 토스 측에서 우리 쪽 연결 해제를 비동기로 요구하는 정책이 있다면, 우리 DB에 별도 보관해둔 토스 access_token으로 호출해야 함. 현재 코드는 보관하지 않으므로 본 task에서는 **DB 정리만** 한다. 토스 API 콜은 `handleUnlinkCallback`이 받음 → 정책상 충분.

- [ ] **Step 1: 실패하는 테스트 추가**

`TossAuthServiceTest.java`에 추가:
```java
@Test
void unlink_removes_user_by_jwt_userId() {
    User user = userRepository.save(User.tossUser("toss-555", "유저"));
    String authHeader = "Bearer " + tokenProvider.createAccessToken(user.getId(), user.getNickname());

    tossAuthService.unlink(authHeader);

    assertThat(userRepository.findById(user.getId())).isEmpty();
    then(appsInTossClient).should(never()).removeByAccessToken(any());
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "TossAuthServiceTest.unlink_removes_user_by_jwt_userId"`
Expected: 실패

- [ ] **Step 3: `unlink()` 재구현**

```java
@Transactional
public void unlink(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    String token = authorizationHeader.substring(7).trim();
    JwtPayload payload = jwtTokenProvider.parseAccessToken(token);
    userRepository.findById(payload.userId()).ifPresent(userRepository::delete);
}
```

`TossAuthController.unlink()`는 현재 `accessToken = authorization.replace("Bearer ", "")`로 가공 후 전달하고 있음. 컨트롤러에서는 raw header 그대로 전달하도록 변경:
```java
@PostMapping("/unlink")
public ResponseEntity<ApiResponse<Void>> unlink(
        @RequestHeader("Authorization") String authorization) {
    tossAuthService.unlink(authorization);
    return ResponseEntity.ok(ApiResponse.ok());
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests TossAuthServiceTest`
Expected: 모든 테스트 PASS

- [ ] **Step 5: `User` 엔티티의 `@Deprecated` 생성자 제거**

이 시점에서 모든 호출처가 팩토리 메서드로 전환됨. 확인:
Run: `grep -rn "new User(" src --include='*.java'`
Expected: 결과 없음 (또는 테스트의 잘못된 케이스만 — 있으면 즉시 수정)

`User.java`에서 `public User(String externalId, String nickname)` 생성자 제거.

- [ ] **Step 6: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 모든 테스트 PASS

- [ ] **Step 7: 커밋**

```bash
git add -u
git commit -m "refactor: TossAuthService.unlink()가 자체 JWT에서 userId 추출하도록 전환, deprecated 생성자 제거"
```

---

## Task 12: 운영 환경변수 문서화 + 배포 자동화 반영

**Files:**
- Modify: `src/main/resources/application-prod.properties` (또는 base `application.properties` — Task 3에서 이미 base에 추가)
- Modify: `.github/workflows/*.yml` (GitHub Actions 배포 워크플로)
- Modify: `docker-compose-prod.yml`

- [ ] **Step 1: GitHub Actions Secret 등록 안내 작성**

`docs/superpowers/specs/`나 PR description에 다음 안내:
```
## 운영 배포 전 필수 작업
GitHub repo → Settings → Secrets and variables → Actions 에 추가:
- JWT_SECRET: 32바이트 이상 랜덤 문자열 (예: openssl rand -base64 48)
- APPINTOSS_API_KEY: 토스 콘솔에서 발급받은 값
```

(시크릿 값 자체는 PR/문서에 절대 포함하지 않음.)

- [ ] **Step 2: GitHub Actions 워크플로에서 환경변수 주입**

`.github/workflows/*.yml` 중 배포 단계에서 컨테이너 실행 시 환경변수 전달:
```yaml
env:
  JWT_SECRET: ${{ secrets.JWT_SECRET }}
  APPINTOSS_API_KEY: ${{ secrets.APPINTOSS_API_KEY }}
```

`docker-compose-prod.yml`의 app 서비스 `environment:` 섹션에 추가:
```yaml
environment:
  - JWT_SECRET=${JWT_SECRET}
  - APPINTOSS_API_KEY=${APPINTOSS_API_KEY}
```

- [ ] **Step 3: 로컬 개발용 안내**

`application-local.properties`에 더미 시크릿 추가 (개발 편의용, 운영 값과 무관):
```properties
jwt.secret=local-dev-secret-do-not-use-in-prod-32bytes
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew bootJar`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add .github/workflows/ docker-compose-prod.yml src/main/resources/application-local.properties
git commit -m "chore: JWT_SECRET, APPINTOSS_API_KEY 환경변수 배포 파이프라인 반영"
```

---

## Task 13: 전체 검증 & PR 준비

- [ ] **Step 1: 전체 테스트**

Run: `./gradlew test`
Expected: 모든 테스트 PASS

- [ ] **Step 2: 빌드**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 로컬에서 게스트 로그인 → 보호된 엔드포인트 호출 수동 확인**

```bash
./gradlew bootRun
# 새 터미널
curl -X POST http://localhost:8080/api/auth/guest/login \
  -H 'Content-Type: application/json' \
  -d '{"deviceId":"test-device-1"}'
# accessToken 복사

curl http://localhost:8080/api/v1/home/snapshot \
  -H "Authorization: Bearer <accessToken>"
```
Expected: 200 OK, 빈 홈 스냅샷 반환

- [ ] **Step 4: 커밋된 변경사항 요약 확인**

Run: `git log --oneline main..HEAD`
Expected: 11~12개 커밋, 각각 하나의 명확한 변경

- [ ] **Step 5: PR 생성**

본 플랜은 사용자 지시 시에만 실행. PR 본문 템플릿:
```
## Summary
- POST /api/auth/guest/login 엔드포인트 추가 (디바이스 게스트 가입/로그인)
- JwtTokenProvider, JwtAuthService 도입 (HMAC SHA-256 자체 JWT)
- DevAuthService(평문 토큰) 제거
- TossAuthService.login/refresh/unlink 자체 JWT 기반으로 전환
- 기존 통합 테스트 8개를 JWT 헬퍼로 마이그레이션

## Test plan
- [x] 단위 테스트 (JwtTokenProviderTest, JwtAuthServiceTest, GuestAuthServiceTest, TossAuthServiceTest)
- [x] 통합 테스트 (GuestAuthApiTest, SessionApiTest 등 8개)
- [ ] dev 환경 배포 후 게스트 로그인 + 보호된 엔드포인트 호출 수동 확인
- [ ] (운영) GitHub Actions Secrets에 JWT_SECRET, APPINTOSS_API_KEY 등록 후 배포

## Follow-up
- 프론트엔드 React Query 통합 (별도 플랜)
- 토스 로그인 활성화 (사업자 등록 완료 후)
```

---

## Risks During Implementation

| 리스크 | 발생 시점 | 대응 |
|--------|-----------|------|
| `DevAuthService`와 `JwtAuthService` 동시 빈 등록 → 컨텍스트 로드 실패 | Task 4~5 사이 | Task 4에서 `JwtAuthService`에 `@Primary` 임시 부여, Task 8에서 `DevAuthService` 삭제와 함께 제거 |
| `users.external_id` NOT NULL 제약으로 게스트 가입 실패 | Task 2 | `nullable = true`로 변경 (Task 2에 포함) |
| `User` 신규 생성 시 ID 받기 전 닉네임 부여 불가 | Task 5 | 임시 닉네임으로 save → updateNickname을 트랜잭션 내에서 (Task 5에 포함) |
| Toss `unlink` 정책상 토스 측 연결 해제 누락 | Task 11 | `handleUnlinkCallback`이 토스→우리 방향 콜백 받는 구조 활용. 우리 쪽에서 능동 호출은 보관된 토스 토큰 없으므로 생략. 추후 정책 명확화 시 별도 task. |
| H2 DB의 unique constraint가 NULL 다중 허용 → 게스트 가입은 정상이지만 토스 사용자는 외부 ID 중복 시 충돌 | 전반 | 의도된 동작. 통합 테스트에서 명시적으로 검증. |
