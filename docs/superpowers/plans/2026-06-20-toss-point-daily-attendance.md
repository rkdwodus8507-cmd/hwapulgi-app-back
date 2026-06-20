# 토스포인트 일일 출석 적립 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 유저가 하루 1번 "포인트 받기" 버튼을 눌러 토스포인트를 적립받는 출석 보상 API를 구현한다.

**Architecture:** 기존 mTLS `RestTemplate`(`appsInTossRestTemplate`)을 재사용하는 `PromotionClient`가 토스 프로모션 3단계 API를 호출한다. `PromotionService`가 `daily_point_grants` 원장의 `UNIQUE(user_id, grant_date)` 제약으로 하루 1회·동시요청을 막고, 토스 호출을 오케스트레이션한다. `PromotionController`는 JWT로 userId를 얻어 서비스에 위임한다.

**Tech Stack:** Spring Boot 3.5, JPA(H2/PostgreSQL), JUnit5 + Mockito + AssertJ, MockRestServiceServer, MockMvc. 빌드: Gradle Kotlin DSL.

**참고 spec:** `docs/superpowers/specs/2026-06-18-toss-point-daily-attendance-design.md`

> **⚠️ 토스 응답 스키마 주의:** 토스 프로모션 API의 정확한 응답 JSON은 공식 문서에 전부 노출되지 않았다. 본 계획은 토스 OAuth API와 동일한 `{resultType, success, error}` 엔벨로프를 가정한다(`TossGenerateTokenResponse` 패턴 일치). 샌드박스/실호출로 실제 응답을 확인하면 Task 4의 DTO 필드만 맞추면 된다. 서비스/멱등성/컨트롤러 로직은 응답 스키마와 무관하게 유효하다.

---

### Task 1: ErrorCode 추가

**Files:**
- Modify: `src/main/java/com/hwapulgi/api/common/exception/ErrorCode.java`

- [ ] **Step 1: 에러코드 2개 추가**

`ErrorCode.java`의 enum 상수 목록 마지막(`RANKING_NOT_FOUND(...)` 다음)에 추가:

```java
    ALREADY_CLAIMED(HttpStatus.CONFLICT, "ALREADY_CLAIMED", "오늘은 이미 포인트를 받았습니다."),
    PROMOTION_FAILED(HttpStatus.BAD_GATEWAY, "PROMOTION_FAILED", "포인트 적립에 실패했습니다. 잠시 후 다시 시도해주세요.");
```

(마지막 enum 상수였던 `RANKING_NOT_FOUND(...)` 줄 끝의 `;` 를 `,` 로 바꾸고 위 두 줄을 이어붙인 뒤 마지막에 `;` 를 둔다.)

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hwapulgi/api/common/exception/ErrorCode.java
git commit -m "feat: 프로모션 에러코드 추가 (ALREADY_CLAIMED, PROMOTION_FAILED)"
```

---

### Task 2: GrantStatus enum + DailyPointGrant 엔티티

**Files:**
- Create: `src/main/java/com/hwapulgi/api/promotion/entity/GrantStatus.java`
- Create: `src/main/java/com/hwapulgi/api/promotion/entity/DailyPointGrant.java`

- [ ] **Step 1: GrantStatus enum 작성**

```java
package com.hwapulgi.api.promotion.entity;

public enum GrantStatus {
    REQUESTED,
    COMPLETED,
    FAILED
}
```

- [ ] **Step 2: DailyPointGrant 엔티티 작성**

```java
package com.hwapulgi.api.promotion.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "daily_point_grants",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_point_grant_user_date",
                columnNames = {"user_id", "grant_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPointGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "grant_date", nullable = false)
    private LocalDate grantDate;

    @Column(nullable = false)
    private String promotionCode;

    @Column(nullable = false)
    private int amount;

    private String tossKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrantStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public DailyPointGrant(Long userId, LocalDate grantDate, String promotionCode, int amount) {
        this.userId = userId;
        this.grantDate = grantDate;
        this.promotionCode = promotionCode;
        this.amount = amount;
        this.status = GrantStatus.REQUESTED;
    }

    public void markCompleted(String tossKey) {
        this.tossKey = tossKey;
        this.status = GrantStatus.COMPLETED;
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hwapulgi/api/promotion/entity/
git commit -m "feat: DailyPointGrant 엔티티 + GrantStatus 추가"
```

---

### Task 3: DailyPointGrantRepository

**Files:**
- Create: `src/main/java/com/hwapulgi/api/promotion/repository/DailyPointGrantRepository.java`

- [ ] **Step 1: 리포지토리 인터페이스 작성**

```java
package com.hwapulgi.api.promotion.repository;

import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyPointGrantRepository extends JpaRepository<DailyPointGrant, Long> {

    Optional<DailyPointGrant> findByUserIdAndGrantDate(Long userId, LocalDate grantDate);

    Optional<DailyPointGrant> findTopByUserIdAndStatusOrderByGrantDateDesc(Long userId, GrantStatus status);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hwapulgi/api/promotion/repository/
git commit -m "feat: DailyPointGrantRepository 추가"
```

---

### Task 4: 토스 프로모션 DTO + PromotionClient

**Files:**
- Create: `src/main/java/com/hwapulgi/api/promotion/dto/PromotionKeyResponse.java`
- Create: `src/main/java/com/hwapulgi/api/promotion/dto/PromotionExecuteRequest.java`
- Create: `src/main/java/com/hwapulgi/api/promotion/dto/PromotionExecuteResponse.java`
- Create: `src/main/java/com/hwapulgi/api/promotion/client/PromotionClient.java`
- Test: `src/test/java/com/hwapulgi/api/promotion/client/PromotionClientTest.java`

- [ ] **Step 1: 응답/요청 DTO 작성 (records)**

`PromotionKeyResponse.java`:
```java
package com.hwapulgi.api.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromotionKeyResponse(String resultType, Success success, String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Success(String key) {
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(resultType) && success != null && success.key() != null;
    }
}
```

`PromotionExecuteRequest.java`:
```java
package com.hwapulgi.api.promotion.dto;

public record PromotionExecuteRequest(String promotionCode, String key, int amount) {
}
```

`PromotionExecuteResponse.java`:
```java
package com.hwapulgi.api.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromotionExecuteResponse(String resultType, String error) {

    public boolean isSuccess() {
        return "SUCCESS".equals(resultType);
    }
}
```

- [ ] **Step 2: PromotionClient 실패 테스트 작성**

`PromotionClientTest.java`:
```java
package com.hwapulgi.api.promotion.client;

import com.hwapulgi.api.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PromotionClientTest {

    private static final String BASE = "https://apps-in-toss-api.toss.im";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PromotionClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new PromotionClient(restTemplate, BASE);
    }

    @Test
    void generateKey_success_returnsKey() {
        server.expect(requestTo(BASE + "/api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-toss-user-key", "12345"))
                .andRespond(withSuccess(
                        "{\"resultType\":\"SUCCESS\",\"success\":{\"key\":\"KEY_ABC\"},\"error\":null}",
                        MediaType.APPLICATION_JSON));

        String key = client.generateKey("12345");

        assertThat(key).isEqualTo("KEY_ABC");
        server.verify();
    }

    @Test
    void generateKey_failure_throwsBusinessException() {
        server.expect(requestTo(BASE + "/api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key"))
                .andRespond(withSuccess(
                        "{\"resultType\":\"FAIL\",\"success\":null,\"error\":\"some-error\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateKey("12345"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executePromotion_success() {
        server.expect(requestTo(BASE + "/api-partner/v1/apps-in-toss/promotion/execute-promotion"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-toss-user-key", "12345"))
                .andExpect(jsonPath("$.promotionCode").value("DAILY"))
                .andExpect(jsonPath("$.key").value("KEY_ABC"))
                .andExpect(jsonPath("$.amount").value(100))
                .andRespond(withSuccess(
                        "{\"resultType\":\"SUCCESS\",\"error\":null}",
                        MediaType.APPLICATION_JSON));

        client.executePromotion("12345", "DAILY", "KEY_ABC", 100);

        server.verify();
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "com.hwapulgi.api.promotion.client.PromotionClientTest"`
Expected: 컴파일 에러 (PromotionClient 없음)

- [ ] **Step 4: PromotionClient 구현**

```java
package com.hwapulgi.api.promotion.client;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.promotion.dto.PromotionExecuteRequest;
import com.hwapulgi.api.promotion.dto.PromotionExecuteResponse;
import com.hwapulgi.api.promotion.dto.PromotionKeyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class PromotionClient {

    private static final String GET_KEY_PATH = "/api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key";
    private static final String EXECUTE_PATH = "/api-partner/v1/apps-in-toss/promotion/execute-promotion";
    private static final String USER_KEY_HEADER = "x-toss-user-key";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PromotionClient(
            @Qualifier("appsInTossRestTemplate") RestTemplate restTemplate,
            @Qualifier("appsInTossBaseUrl") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public String generateKey(String userKey) {
        HttpHeaders headers = baseHeaders(userKey);
        try {
            ResponseEntity<PromotionKeyResponse> response = restTemplate.exchange(
                    baseUrl + GET_KEY_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    PromotionKeyResponse.class
            );
            PromotionKeyResponse body = response.getBody();
            if (body == null || !body.isSuccess()) {
                log.error("프로모션 키 발급 실패: {}", body);
                throw new BusinessException(ErrorCode.PROMOTION_FAILED);
            }
            return body.success().key();
        } catch (RestClientException e) {
            log.error("프로모션 키 발급 통신 오류", e);
            throw new BusinessException(ErrorCode.PROMOTION_FAILED);
        }
    }

    public void executePromotion(String userKey, String promotionCode, String key, int amount) {
        HttpHeaders headers = baseHeaders(userKey);
        PromotionExecuteRequest request = new PromotionExecuteRequest(promotionCode, key, amount);
        try {
            ResponseEntity<PromotionExecuteResponse> response = restTemplate.exchange(
                    baseUrl + EXECUTE_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    PromotionExecuteResponse.class
            );
            PromotionExecuteResponse body = response.getBody();
            if (body == null || !body.isSuccess()) {
                log.error("프로모션 적립 실패: {}", body);
                throw new BusinessException(ErrorCode.PROMOTION_FAILED);
            }
        } catch (RestClientException e) {
            log.error("프로모션 적립 통신 오류", e);
            throw new BusinessException(ErrorCode.PROMOTION_FAILED);
        }
    }

    private HttpHeaders baseHeaders(String userKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(USER_KEY_HEADER, userKey);
        return headers;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.hwapulgi.api.promotion.client.PromotionClientTest"`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hwapulgi/api/promotion/dto/ src/main/java/com/hwapulgi/api/promotion/client/ src/test/java/com/hwapulgi/api/promotion/client/
git commit -m "feat: PromotionClient + 토스 프로모션 DTO 추가"
```

---

### Task 5: 설정값 추가

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: 프로모션 설정 추가**

`application.properties`의 Apps in Toss 블록(`appintoss.mtls.key=...` 줄 다음)에 추가:

```properties
# Apps in Toss - 일일 출석 프로모션
# 콘솔에서 발급한 promotionCode로 교체할 것
appintoss.promotion.daily.code=${APPINTOSS_PROMOTION_CODE:DAILY_ATTENDANCE}
appintoss.promotion.daily.amount=${APPINTOSS_PROMOTION_AMOUNT:100}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "chore: 일일 출석 프로모션 설정값 추가"
```

---

### Task 6: 응답 DTO

**Files:**
- Create: `src/main/java/com/hwapulgi/api/promotion/dto/DailyClaimResponse.java`
- Create: `src/main/java/com/hwapulgi/api/promotion/dto/DailyStatusResponse.java`

- [ ] **Step 1: 응답 DTO 2개 작성**

`DailyClaimResponse.java`:
```java
package com.hwapulgi.api.promotion.dto;

import java.time.LocalDateTime;

public record DailyClaimResponse(boolean granted, int amount, LocalDateTime grantedAt) {
}
```

`DailyStatusResponse.java`:
```java
package com.hwapulgi.api.promotion.dto;

import java.time.LocalDateTime;

public record DailyStatusResponse(boolean claimable, int amount, LocalDateTime lastClaimedAt) {
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hwapulgi/api/promotion/dto/DailyClaimResponse.java src/main/java/com/hwapulgi/api/promotion/dto/DailyStatusResponse.java
git commit -m "feat: 프로모션 응답 DTO 추가"
```

---

### Task 7: PromotionService (TDD 핵심)

**Files:**
- Create: `src/main/java/com/hwapulgi/api/promotion/service/PromotionService.java`
- Test: `src/test/java/com/hwapulgi/api/promotion/service/PromotionServiceTest.java`

> **동시성/멱등성 설계:** `claimDaily`는 단일 `@Transactional`. 오늘자 row가 COMPLETED면 `ALREADY_CLAIMED`. 없으면 `saveAndFlush`로 즉시 INSERT(REQUESTED) → `UNIQUE(user_id, grant_date)` 제약이 동시 따닥클릭의 두 번째 요청을 flush 시점에 차단(`DataIntegrityViolationException` → `ALREADY_CLAIMED`). 이후 토스 호출 성공 시 `markCompleted`. 토스 실패 시 예외가 전파되어 트랜잭션이 롤백 → REQUESTED row가 사라져 재시도 가능(전송 중복 없음). KST 날짜는 주입된 `Clock`으로 계산해 테스트 가능하게 한다.

- [ ] **Step 1: 서비스 단위 테스트 작성 (4 케이스)**

`PromotionServiceTest.java`:
```java
package com.hwapulgi.api.promotion.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.promotion.client.PromotionClient;
import com.hwapulgi.api.promotion.dto.DailyClaimResponse;
import com.hwapulgi.api.promotion.dto.DailyStatusResponse;
import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private DailyPointGrantRepository grantRepository;
    @Mock private PromotionClient promotionClient;
    @Mock private UserService userService;

    private PromotionService service;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-06-20T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDate today = LocalDate.of(2026, 6, 20);

    @BeforeEach
    void setUp() {
        service = new PromotionService(grantRepository, promotionClient, userService,
                fixedClock, "DAILY", 100);
    }

    @Test
    void claimDaily_firstClaim_succeeds() {
        given(grantRepository.findByUserIdAndGrantDate(1L, today)).willReturn(Optional.empty());
        given(userService.findById(1L)).willReturn(User.tossUser("12345", "닉"));
        given(grantRepository.saveAndFlush(any(DailyPointGrant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(promotionClient.generateKey("12345")).willReturn("KEY_ABC");

        DailyClaimResponse response = service.claimDaily(1L);

        assertThat(response.granted()).isTrue();
        assertThat(response.amount()).isEqualTo(100);
        verify(promotionClient).executePromotion("12345", "DAILY", "KEY_ABC", 100);
    }

    @Test
    void claimDaily_alreadyCompletedToday_throwsAndSkipsToss() {
        DailyPointGrant completed = DailyPointGrant.builder()
                .userId(1L).grantDate(today).promotionCode("DAILY").amount(100).build();
        completed.markCompleted("KEY_OLD");
        given(grantRepository.findByUserIdAndGrantDate(1L, today))
                .willReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.claimDaily(1L))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(promotionClient);
    }

    @Test
    void claimDaily_tossFails_propagatesException() {
        given(grantRepository.findByUserIdAndGrantDate(1L, today)).willReturn(Optional.empty());
        given(userService.findById(1L)).willReturn(User.tossUser("12345", "닉"));
        given(grantRepository.saveAndFlush(any(DailyPointGrant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(promotionClient.generateKey("12345"))
                .willThrow(new BusinessException(com.hwapulgi.api.common.exception.ErrorCode.PROMOTION_FAILED));

        assertThatThrownBy(() -> service.claimDaily(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getDailyStatus_notClaimedToday_claimableTrue() {
        given(grantRepository.findByUserIdAndGrantDate(1L, today)).willReturn(Optional.empty());
        given(grantRepository.findTopByUserIdAndStatusOrderByGrantDateDesc(1L, GrantStatus.COMPLETED))
                .willReturn(Optional.empty());

        DailyStatusResponse status = service.getDailyStatus(1L);

        assertThat(status.claimable()).isTrue();
        assertThat(status.amount()).isEqualTo(100);
        assertThat(status.lastClaimedAt()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.hwapulgi.api.promotion.service.PromotionServiceTest"`
Expected: 컴파일 에러 (PromotionService 없음)

- [ ] **Step 3: PromotionService 구현**

```java
package com.hwapulgi.api.promotion.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.promotion.client.PromotionClient;
import com.hwapulgi.api.promotion.dto.DailyClaimResponse;
import com.hwapulgi.api.promotion.dto.DailyStatusResponse;
import com.hwapulgi.api.promotion.entity.DailyPointGrant;
import com.hwapulgi.api.promotion.entity.GrantStatus;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PromotionService {

    private final DailyPointGrantRepository grantRepository;
    private final PromotionClient promotionClient;
    private final UserService userService;
    private final Clock clock;
    private final String promotionCode;
    private final int amount;

    public PromotionService(
            DailyPointGrantRepository grantRepository,
            PromotionClient promotionClient,
            UserService userService,
            @Value("${appintoss.promotion.daily.code}") String promotionCode,
            @Value("${appintoss.promotion.daily.amount}") int amount) {
        this(grantRepository, promotionClient, userService,
                Clock.system(ZoneId.of("Asia/Seoul")), promotionCode, amount);
    }

    PromotionService(
            DailyPointGrantRepository grantRepository,
            PromotionClient promotionClient,
            UserService userService,
            Clock clock,
            String promotionCode,
            int amount) {
        this.grantRepository = grantRepository;
        this.promotionClient = promotionClient;
        this.userService = userService;
        this.clock = clock;
        this.promotionCode = promotionCode;
        this.amount = amount;
    }

    @Transactional
    public DailyClaimResponse claimDaily(Long userId) {
        LocalDate today = LocalDate.now(clock);

        Optional<DailyPointGrant> existing = grantRepository.findByUserIdAndGrantDate(userId, today);
        if (existing.isPresent() && existing.get().getStatus() == GrantStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
        }

        User user = userService.findById(userId);

        DailyPointGrant grant = existing.orElseGet(() -> reserve(userId, today));

        String key = promotionClient.generateKey(user.getExternalId());
        promotionClient.executePromotion(user.getExternalId(), promotionCode, key, amount);
        grant.markCompleted(key);

        return new DailyClaimResponse(true, amount, grant.getCreatedAt());
    }

    public DailyStatusResponse getDailyStatus(Long userId) {
        LocalDate today = LocalDate.now(clock);

        boolean claimable = grantRepository.findByUserIdAndGrantDate(userId, today)
                .map(g -> g.getStatus() != GrantStatus.COMPLETED)
                .orElse(true);

        LocalDateTime lastClaimedAt = grantRepository
                .findTopByUserIdAndStatusOrderByGrantDateDesc(userId, GrantStatus.COMPLETED)
                .map(DailyPointGrant::getCreatedAt)
                .orElse(null);

        return new DailyStatusResponse(claimable, amount, lastClaimedAt);
    }

    private DailyPointGrant reserve(Long userId, LocalDate today) {
        try {
            return grantRepository.saveAndFlush(
                    DailyPointGrant.builder()
                            .userId(userId)
                            .grantDate(today)
                            .promotionCode(promotionCode)
                            .amount(amount)
                            .build());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 선점함
            throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.hwapulgi.api.promotion.service.PromotionServiceTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hwapulgi/api/promotion/service/ src/test/java/com/hwapulgi/api/promotion/service/
git commit -m "feat: PromotionService 일일 출석 적립 로직 + 단위 테스트"
```

---

### Task 8: PromotionController

**Files:**
- Create: `src/main/java/com/hwapulgi/api/promotion/controller/PromotionController.java`

> **인증 패턴:** `GameSessionController`와 동일하게 `@RequestHeader("Authorization")` → `authService.authenticate(token)` → `UserInfo.userId()`. `AuthService`/`UserInfo`의 정확한 패키지는 `GameSessionController.java` 상단 import를 그대로 따른다.

- [ ] **Step 1: 컨트롤러 작성**

```java
package com.hwapulgi.api.promotion.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.promotion.dto.DailyClaimResponse;
import com.hwapulgi.api.promotion.dto.DailyStatusResponse;
import com.hwapulgi.api.promotion.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Promotion", description = "토스포인트 프로모션")
@RestController
@RequestMapping("/api/v1/promotion")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final AuthService authService;

    @Operation(summary = "일일 출석 포인트 받기")
    @PostMapping("/daily/claim")
    public ApiResponse<DailyClaimResponse> claimDaily(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        return ApiResponse.ok(promotionService.claimDaily(userInfo.userId()));
    }

    @Operation(summary = "일일 출석 수령 가능 여부 조회")
    @GetMapping("/daily/status")
    public ApiResponse<DailyStatusResponse> dailyStatus(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        return ApiResponse.ok(promotionService.getDailyStatus(userInfo.userId()));
    }
}
```

> **확인 필요:** `GameSessionController.java`의 import 중 `AuthService`/`UserInfo`의 실제 타입·패키지를 열어 확인하고 위 import 2줄을 그 값으로 맞춘다. (보고서 기준 `authService.authenticate(token)` → `UserInfo userInfo` / `userInfo.userId()`.) 만약 `authenticate`가 `UserService.findById`까지 거쳐야 하면 GameSessionController와 동일하게 처리한다.

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (실패 시 import 경로를 GameSessionController에 맞춰 수정)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hwapulgi/api/promotion/controller/
git commit -m "feat: PromotionController (daily claim/status 엔드포인트)"
```

---

### Task 9: 통합 테스트

**Files:**
- Test: `src/test/java/com/hwapulgi/api/integration/PromotionApiTest.java`

> 실제 토스 호출을 막기 위해 `PromotionClient`를 `@MockBean`으로 대체한다. 기존 `SessionApiTest`의 구조(@SpringBootTest, @AutoConfigureMockMvc, @ActiveProfiles("local"), AuthTokenFixture)를 그대로 따른다.

- [ ] **Step 1: 통합 테스트 작성**

```java
package com.hwapulgi.api.integration;

import com.hwapulgi.api.integration.support.AuthTokenFixture;
import com.hwapulgi.api.promotion.client.PromotionClient;
import com.hwapulgi.api.promotion.repository.DailyPointGrantRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(AuthTokenFixture.class)
class PromotionApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private DailyPointGrantRepository grantRepository;
    @Autowired private AuthTokenFixture authTokens;

    @MockBean private PromotionClient promotionClient;

    private String bearer;

    @BeforeEach
    void setUp() {
        grantRepository.deleteAll();
        userRepository.deleteAll();
        User user = userRepository.save(User.tossUser("12345", "테스트유저"));
        bearer = authTokens.bearerForUser(user);

        given(promotionClient.generateKey("12345")).willReturn("KEY_ABC");
        doNothing().when(promotionClient).executePromotion("12345", "DAILY_ATTENDANCE", "KEY_ABC", 100);
    }

    @Test
    void claimDaily_firstTime_succeedsThenConflict() throws Exception {
        mockMvc.perform(post("/api/v1/promotion/daily/claim").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.granted").value(true))
                .andExpect(jsonPath("$.data.amount").value(100));

        // 같은 날 재수령 → 409
        mockMvc.perform(post("/api/v1/promotion/daily/claim").header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void status_reflectsClaim() throws Exception {
        mockMvc.perform(get("/api/v1/promotion/daily/status").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimable").value(true));

        mockMvc.perform(post("/api/v1/promotion/daily/claim").header("Authorization", bearer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/promotion/daily/status").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimable").value(false));
    }
}
```

> **주의:** `@MockBean`의 `executePromotion` 인자 `"DAILY_ATTENDANCE"`/`100`은 `application-local.properties`(또는 기본값)의 `appintoss.promotion.daily.code/amount`와 일치해야 한다. local 프로파일에 값이 없으면 application.properties 기본값(`DAILY_ATTENDANCE`,`100`)이 적용된다. 불일치 시 stub이 안 먹으므로 값을 맞출 것.

- [ ] **Step 2: 통합 테스트 통과 확인**

Run: `./gradlew test --tests "com.hwapulgi.api.integration.PromotionApiTest"`
Expected: PASS (2 tests)

- [ ] **Step 3: 전체 테스트 회귀 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (기존 테스트 포함 전부 통과)

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/hwapulgi/api/integration/PromotionApiTest.java
git commit -m "test: 프로모션 일일 출석 통합 테스트"
```

---

## 마무리 확인 (구현 후)

- [ ] `./gradlew test` 전체 통과
- [ ] Swagger(`/swagger-ui.html`)에 `POST /api/v1/promotion/daily/claim`, `GET /api/v1/promotion/daily/status` 노출 확인
- [ ] **운영 선행조건 안내**: 콘솔에서 실제 promotionCode 생성 → `APPINTOSS_PROMOTION_CODE` 환경변수 주입 + Biz Wallet 예산 충전 전엔 실제 지급 안 됨
- [ ] **토스 응답 스키마 검증**: 샌드박스/실호출로 get-key·execute-promotion 실제 응답 확인 후 Task 4 DTO 필드 조정 (현재 `{resultType, success.key, error}` 가정)
- [ ] **인증 헤더 검증**: 1번 mTLS 스모크 테스트 결과로 콘솔 API 키가 별도 헤더로 필요한지 확정 → 필요시 `PromotionClient.baseHeaders()`에 한 줄 추가

## Self-Review 결과

- **Spec coverage:** claim/status 엔드포인트(Task 8), 하루1회·동시성(Task 7), 토스 3단계 중 get-key+execute(Task 4, execution-result는 멱등성을 DB 제약으로 대체하여 MVP 범위서 제외 — 마무리 확인에 검증 항목으로 남김), 원장(Task 2), KST 경계(Task 7 Clock), 정책 한도(설정값 Task 5)로 커버.
- **변경점(spec 대비):** 부분실패 시 FAILED 영속화 대신 트랜잭션 롤백으로 재시도 허용 — 일일 출석의 재시도 UX를 우선. `execution-result` 재조회 복구는 실응답 확인 후 후속.
- **Placeholder scan:** 없음. 모든 step에 실제 코드/명령 포함.
- **Type consistency:** `claimDaily`/`getDailyStatus`, `generateKey`/`executePromotion`, `markCompleted`, `findByUserIdAndGrantDate`, `findTopByUserIdAndStatusOrderByGrantDateDesc` 전 태스크 일관.