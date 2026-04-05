# 코드 리뷰 이력

## 2026-04-05 — 초기 구현 후 전체 코드 리뷰

### 리뷰 범위

전체 백엔드 구현 (commit `651ab7e` ~ `fba23d3`)

- Common 응답/예외
- Auth 인터페이스
- User 도메인
- Session 도메인
- Ranking 도메인 (Redis)
- REST Controllers
- Integration Tests

---

### Critical 이슈 (4건) — 모두 수정 완료

#### C1. 세션 저장과 랭킹 업데이트 비원자성

**문제:** 컨트롤러에서 DB 저장과 Redis 랭킹 업데이트가 분리되어, Redis 실패 시 데이터 불일치 발생 가능.

**수정:** 랭킹 업데이트를 `GameSessionService.createSession()` 내부로 이동. try-catch로 Redis 실패 시 로깅 후 세션 저장은 유지.

**파일:** `GameSessionService.java`, `GameSessionController.java`

---

#### C2. getOrCreateUser race condition

**문제:** 동시 요청 시 `findByExternalId` → 둘 다 없음 → 둘 다 insert → unique 제약 위반 500 에러.

**수정:** `save` → `saveAndFlush`로 변경. `DataIntegrityViolationException` catch 후 재조회.

**파일:** `UserService.java`

---

#### C3. updateAngerAfter 포인트 미재계산 + releasedPercent 음수 가능

**문제:** `updateAngerAfter()`가 `releasedPercent`만 재계산하고 `points`는 변경 안 함 (불일치). `angerAfter > angerBefore`일 때 음수 발생.

**수정:** points는 세션 생성 시 확정 (의도적 설계로 문서화). `releasedPercent`에 `Math.max(0, ...)` 적용.

**파일:** `GameSession.java`

---

#### C4. updateReleaseRate 로직 오류

**문제:** all-time 점수 기준으로만 주간/월간 업데이트 판단. 주간 best가 all-time보다 낮으면 주간 랭킹이 갱신되지 않는 버그.

**수정:** 주간, 월간, 전체 각각 독립적으로 `updateIfHigher()` 호출.

**파일:** `RankingService.java`

---

### Important 이슈 (6건) — 모두 수정 완료

#### I1. 세션 GET/PATCH IDOR 취약점

**문제:** `GET /sessions/{id}`, `PATCH /sessions/{id}/anger-after`에 인증/소유권 체크 없음. UUID만 알면 타인 세션 접근 가능.

**수정:** 인증 헤더 필수화 + `findSessionWithOwnerCheck()` 메서드 추가 (user_id 비교, 불일치 시 403).

**파일:** `GameSessionController.java`, `GameSessionService.java`, `ErrorCode.java` (FORBIDDEN 추가)

---

#### I2. DevAuthService NumberFormatException

**문제:** 잘못된 토큰 형식 시 `Long.parseLong()` → `NumberFormatException` → 500 에러 (401이어야 함).

**수정:** try-catch + `BusinessException(ErrorCode.UNAUTHORIZED)` throw.

**파일:** `DevAuthService.java`

---

#### I3. RankingSnapshotScheduler rank 미반영

**문제:** `rank` 변수를 증가시키지만 lambda 내에서 사용 불가 → 모든 스냅샷의 rank가 0.

**수정:** `AtomicInteger`로 변경, `currentRank`를 lambda 외부에서 캡처.

**파일:** `RankingSnapshotScheduler.java`

---

#### I5. embedded-redis 프로덕션 JAR 포함

**문제:** `implementation`으로 선언되어 프로덕션 빌드에 Redis 서버 바이너리가 포함됨.

**수정:** `testImplementation`으로 변경. `EmbeddedRedisConfig`를 `src/test/` 하위로 이동.

**파일:** `build.gradle.kts`, `EmbeddedRedisConfig.java` (위치 변경)

---

#### I6. 문자열 필드 길이 제한 없음

**문제:** `target`, `targetNickname`, `memo` 등에 `@Size` 없어 대량 데이터 전송 가능.

**수정:** `@Size` 추가 — target(20), customTarget(50), targetNickname(50), memo(1000).

**파일:** `GameSessionCreateRequest.java`

---

#### I7. getTopRanking N+1 쿼리

**문제:** 랭킹 유저별로 `userRepository.findById()` 개별 호출 → limit=50이면 50번 SELECT.

**수정:** `userRepository.findAllById(userIds)`로 배치 조회 후 Map으로 닉네임 매핑.

**파일:** `RankingService.java`

---

### Suggestion (3건) — 수정 완료

| # | 이슈 | 수정 |
|---|------|------|
| S4 | Redis 키 TTL 미설정 | weekly 14일, monthly 62일 TTL 추가 |
| S6 | releasedPercent 음수 가능 | C3에서 함께 수정 (Math.max(0, ...)) |
| S7 | 통합 테스트 Redis 미정리 | @BeforeEach에서 `ranking:*` 키 삭제 |

---

### 커밋 이력

| Commit | 내용 |
|--------|------|
| `6bebe84` | Critical C1~C4 + Important I2, I3 수정 |
| `3f80398` | Important I1, I5~I7 + Suggestion S4, S7 수정 |
