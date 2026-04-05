# 데이터 모델

## ERD

```
┌──────────────────────┐
│       users          │
├──────────────────────┤
│ id         BIGINT PK │──┬──────────────────┐
│ external_id VARCHAR  │  │  (UNIQUE)        │
│ nickname   VARCHAR   │  │                  │
│ created_at TIMESTAMP │  │                  │
│ updated_at TIMESTAMP │  │                  │
└──────────────────────┘  │                  │
                          │                  │
         ┌────────────────┤                  │
         │                │                  │
         ▼                ▼                  ▼
┌─────────────────────────┐  ┌─────────────────────────┐  ┌─────────────────────────┐
│     game_sessions       │  │   ranking_snapshots     │  │     achievements        │
├─────────────────────────┤  ├─────────────────────────┤  ├─────────────────────────┤
│ id          UUID    PK  │  │ id           BIGINT PK  │  │ id               BIGINT │
│ user_id     BIGINT  FK  │  │ user_id      BIGINT FK  │  │ user_id          BIGINT │
│ target      VARCHAR     │  │ period_type  ENUM       │  │ achievement_type VARCHAR│
│ custom_target VARCHAR   │  │ period_key   VARCHAR    │  │ achieved_at    TIMESTAMP│
│ target_nickname VARCHAR │  │ total_points INTEGER    │  └─────────────────────────┘
│ anger_before INTEGER    │  │ total_sessions INTEGER  │    UNIQUE(user_id, type)
│ anger_after  INTEGER    │  │ total_hits   INTEGER    │
│ hits        INTEGER     │  │ best_release INTEGER    │
│ skill_shots INTEGER     │  │ avg_release  INTEGER    │
│ released_percent INT    │  │ rank         INTEGER    │
│ points      INTEGER     │  │ updated_at   TIMESTAMP  │
│ memo        TEXT        │  └─────────────────────────┘
│ created_at  TIMESTAMP   │
└─────────────────────────┘
```

## 테이블 상세

### users

사용자 정보. 외부 인증 시스템(토스)의 식별자를 `external_id`로 저장합니다.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | 내부 사용자 ID |
| external_id | VARCHAR | NOT NULL, UNIQUE | 외부 인증 식별자 |
| nickname | VARCHAR | NOT NULL | 사용자 닉네임 |
| created_at | TIMESTAMP | auto | 가입일 |
| updated_at | TIMESTAMP | auto | 수정일 |

**JPA Entity:** `com.hwapulgi.api.user.entity.User`

**인덱스:**
- `external_id` UNIQUE — 외부 ID로 빠른 조회 + 중복 방지

---

### game_sessions

게임 세션 기록. 사용자의 분노 해소 게임 결과를 저장합니다.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | 세션 ID (앱에서 생성) |
| user_id | BIGINT | FK → users, NOT NULL | 사용자 |
| target | VARCHAR | NOT NULL | 대상 (회사, 상사, 동료, 고객, 가족, 친구, 연인, 나 자신, 기타) |
| custom_target | VARCHAR | nullable | 기타 선택 시 커스텀 대상 |
| target_nickname | VARCHAR | NOT NULL | 대상 닉네임 |
| anger_before | INTEGER | NOT NULL | 게임 전 분노 수치 (0-100) |
| anger_after | INTEGER | NOT NULL | 게임 후 분노 수치 (0-100) |
| hits | INTEGER | NOT NULL | 총 타격 수 |
| skill_shots | INTEGER | NOT NULL | 스킬샷 수 |
| released_percent | INTEGER | NOT NULL | 분노 해소율 (%, 0 이상) |
| points | INTEGER | NOT NULL | 획득 포인트 (서버 검증) |
| memo | TEXT | nullable | 메모 (최대 1000자) |
| created_at | TIMESTAMP | auto | 세션 생성일 |

**JPA Entity:** `com.hwapulgi.api.session.entity.GameSession`

**포인트 계산 공식:**
```
points = 10 + hits + (skillShots × 4) + floor((angerBefore - min(angerAfter, angerBefore)) / 2)
```

**주요 쿼리:**
- `findByUserIdOrderByCreatedAtDesc(userId, pageable)` — 내 세션 목록 (페이징)
- `findByUserIdAndCreatedAtAfter(userId, from)` — 기간별 통계 조회

---

### ranking_snapshots

랭킹 스냅샷. 스케줄러가 주기적으로 Redis 랭킹을 DB에 백업합니다.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | 스냅샷 ID |
| user_id | BIGINT | FK → users, NOT NULL | 사용자 |
| period_type | ENUM | NOT NULL | WEEKLY, MONTHLY, ALL_TIME |
| period_key | VARCHAR | NOT NULL | 기간 키 (예: "2026-W14", "2026-04") |
| total_points | INTEGER | | 기간 내 총 포인트 |
| total_sessions | INTEGER | | 기간 내 총 세션 수 |
| total_hits | INTEGER | | 기간 내 총 타격 수 |
| best_release | INTEGER | | 최고 해소율 (%) |
| avg_release | INTEGER | | 평균 해소율 (%) |
| rank | INTEGER | | 해당 기간 순위 |
| updated_at | TIMESTAMP | auto | 마지막 업데이트 |

**JPA Entity:** `com.hwapulgi.api.ranking.entity.RankingSnapshot`

---

### achievements

업적/배지 기록. 세션 생성 시 자동으로 14종 업적 달성 여부를 체크합니다.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | 업적 ID |
| user_id | BIGINT | FK → users, NOT NULL | 사용자 |
| achievement_type | VARCHAR (ENUM) | NOT NULL | 업적 타입 (14종) |
| achieved_at | TIMESTAMP | auto | 달성 시각 |

**JPA Entity:** `com.hwapulgi.api.achievement.entity.Achievement`

**Unique Constraint:** `(user_id, achievement_type)` — 중복 방지

**업적 타입:** HITS_100, HITS_500, HITS_1000, SESSIONS_10, SESSIONS_50, SESSIONS_100, RELEASE_80, RELEASE_90, RELEASE_100, STREAK_3, STREAK_7, STREAK_30, POINTS_500, POINTS_1000

---

## Redis 데이터 구조

### 랭킹 Sorted Set

키 패턴: `ranking:{criteria}:{period}:{period_key}`

| Key 예시 | Member | Score | 설명 |
|----------|--------|-------|------|
| `ranking:points:weekly:2026-W14` | userId | 누적 포인트 | 주간 포인트 랭킹 |
| `ranking:points:monthly:2026-04` | userId | 누적 포인트 | 월간 포인트 랭킹 |
| `ranking:points:all_time:all` | userId | 누적 포인트 | 전체 포인트 랭킹 |
| `ranking:release:weekly:2026-W14` | userId | 최고 해소율 | 주간 해소율 랭킹 |
| `ranking:release:monthly:2026-04` | userId | 최고 해소율 | 월간 해소율 랭킹 |
| `ranking:release:all_time:all` | userId | 최고 해소율 | 전체 해소율 랭킹 |

**TTL 정책:**
- weekly 키: 14일
- monthly 키: 62일
- all_time 키: TTL 없음 (영구)

**주요 연산:**
- 포인트: `ZINCRBY` (누적)
- 해소율: `ZADD` (per-period best — 기존 점수보다 높을 때만 갱신)
- 랭킹 조회: `ZREVRANGE`, `ZREVRANK`, `ZSCORE`, `ZCARD`
