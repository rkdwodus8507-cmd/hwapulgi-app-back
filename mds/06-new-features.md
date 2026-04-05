# 신규 기능 — 업적, 스트릭, 대상 통계

## 2026-04-05 추가

프론트엔드 분석을 기반으로 게임성 강화를 위한 3개 기능을 추가했습니다.

---

## 1. 업적/배지 시스템

### 개요

세션 생성 시 자동으로 14종 업적 달성 여부를 체크하고, 조건 충족 시 배지를 부여합니다. 한 번 획득한 업적은 중복 부여되지 않습니다 (DB unique constraint + in-memory Set 체크).

### API

**GET /api/v1/achievements/me** — 내 업적 목록 조회

```json
{
  "success": true,
  "data": [
    {
      "type": "HITS_100",
      "title": "백 번의 주먹",
      "description": "누적 100회 타격 달성",
      "achievedAt": "2026-04-05T12:00:00"
    }
  ]
}
```

### 업적 종류 (14종)

| 카테고리 | Type | 조건 | 타이틀 |
|----------|------|------|--------|
| 타격 | HITS_100 | 누적 100회 | 백 번의 주먹 |
| 타격 | HITS_500 | 누적 500회 | 오백 번의 분노 |
| 타격 | HITS_1000 | 누적 1,000회 | 천 번의 해소 |
| 세션 | SESSIONS_10 | 10회 완료 | 열 번째 화풀기 |
| 세션 | SESSIONS_50 | 50회 완료 | 화풀기 고수 |
| 세션 | SESSIONS_100 | 100회 완료 | 화풀기 마스터 |
| 해소율 | RELEASE_80 | 80% 이상 | 마음이 편안 |
| 해소율 | RELEASE_90 | 90% 이상 | 거의 완벽 |
| 해소율 | RELEASE_100 | 100% | 완전 해소 |
| 스트릭 | STREAK_3 | 3일 연속 | 3일 연속 |
| 스트릭 | STREAK_7 | 7일 연속 | 일주일 내내 |
| 스트릭 | STREAK_30 | 30일 연속 | 한 달 연속 |
| 포인트 | POINTS_500 | 1세션 500점 | 500점 돌파 |
| 포인트 | POINTS_1000 | 1세션 1,000점 | 천점 달인 |

### 구현 파일

```
com.hwapulgi.api.achievement/
├── entity/AchievementType.java      enum (14종, title/description/threshold)
├── entity/Achievement.java          JPA 엔티티 (unique: user_id + type)
├── repository/AchievementRepository.java
├── service/AchievementService.java  자동 체크 + 배치 조회 최적화
├── controller/AchievementController.java
└── dto/AchievementResponse.java
```

### 성능 최적화

- 전체 세션 로딩 대신 집계 쿼리 사용 (`sumHitsByUserId`, `countByUserId`)
- 14번 `existsBy` 개별 호출 → 1번 배치 조회 후 `Set<AchievementType>` 메모리 체크
- 최신 세션 객체를 caller에서 직접 전달 (추가 쿼리 없음)

---

## 2. 연속 플레이 스트릭

### 개요

세션 날짜를 기반으로 연속 플레이 일수를 계산합니다. 오늘 또는 어제 플레이한 경우에만 현재 스트릭이 유지됩니다.

### API

**GET /api/v1/streaks/me** — 내 스트릭 조회

```json
{
  "success": true,
  "data": {
    "currentStreak": 5,
    "bestStreak": 12,
    "totalPlayDays": 45
  }
}
```

### 계산 로직

- **currentStreak**: 오늘/어제부터 연속으로 플레이한 일수. 마지막 플레이가 2일 이상 전이면 0.
- **bestStreak**: 역대 최장 연속 플레이 일수.
- **totalPlayDays**: 총 플레이 일수 (중복 날짜 제거).

### 구현 파일

```
com.hwapulgi.api.streak/
├── service/StreakService.java       Clock 주입으로 테스트 가능
├── controller/StreakController.java
└── dto/StreakResponse.java
```

### 설계 결정

- `Clock` 주입: `LocalDate.now(clock)` 사용으로 시간대 관련 버그 방지 및 테스트 용이
- JPQL `CAST(createdAt AS LocalDate)`: Hibernate 6.x에서 지원, 날짜별 DISTINCT 조회
- 별도 테이블 없이 기존 `game_sessions.created_at`에서 계산

---

## 3. 분노 대상별 통계

### 개요

사용자가 어떤 대상에 가장 많이 화를 냈는지, 대상별 세션 수/타격 수/평균 해소율을 집계합니다.

### API

**GET /api/v1/users/me/target-stats** — 내 대상별 통계

```json
{
  "success": true,
  "data": [
    {
      "target": "회사",
      "sessionCount": 25,
      "totalHits": 1250,
      "avgRelease": 72.5
    },
    {
      "target": "상사",
      "sessionCount": 15,
      "totalHits": 890,
      "avgRelease": 65.0
    }
  ]
}
```

### 구현

- JPQL GROUP BY 집계 쿼리 (`GameSessionRepository.findTargetStatsByUserId`)
- 세션 수 기준 내림차순 정렬
- 별도 테이블 없음 — 기존 `game_sessions` 테이블에서 실시간 집계

---

## 연동: 세션 생성 흐름

```
POST /api/v1/sessions
  ↓
1. 포인트 재계산 검증
2. DB 저장
3. Redis 랭킹 업데이트 (try-catch)
4. 스트릭 계산 + 업적 체크 (try-catch)  ← 신규
  ↓
Response
```

업적/스트릭 체크 실패 시에도 세션 저장은 보장됩니다 (try-catch 로깅).

---

## 데이터 모델

### achievements 테이블 (신규)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | 업적 ID |
| user_id | BIGINT | FK → users, NOT NULL | 사용자 |
| achievement_type | VARCHAR (ENUM) | NOT NULL | 업적 타입 |
| achieved_at | TIMESTAMP | auto | 달성 시각 |

**Unique Constraint:** `(user_id, achievement_type)` — 중복 방지

---

## 테스트

| 테스트 | 항목 |
|--------|------|
| AchievementServiceTest | 조건 충족 시 업적 부여, 이미 보유 시 스킵 |
| StreakServiceTest | 연속일 계산, 끊긴 스트릭, 빈 데이터, 중간 갭 |
| AchievementApiTest | GET /achievements/me 빈 목록 |
| StreakApiTest | GET /streaks/me 초기 상태 |
| TargetStatsApiTest | 세션 생성 후 대상별 통계 조회 |

---

## 코드 리뷰 이력

### 2026-04-05 — 신규 기능 코드 리뷰

**Critical (2건, 수정 완료):**
- C1: 전체 세션 메모리 로딩 → 집계 쿼리 교체
- C2: 세션 생성 시 중복 DB 쿼리 → 최신 세션 직접 전달

**Important (2건, 수정 완료):**
- I4: `LocalDate.now()` → `Clock` 주입
- I5: 14번 `existsBy` → 배치 조회 + Set 체크

**커밋:** `2454250`
