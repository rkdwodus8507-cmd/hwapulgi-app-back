# API 명세서

## 공통

### 인증

모든 인증이 필요한 API는 `Authorization` 헤더를 사용합니다.

- **local/dev**: `Authorization: {userId}:{nickname}` (예: `1:테스트유저`)
- **prod**: 토스 인증 토큰 (미정)

### 응답 형식

**성공:**
```json
{
  "success": true,
  "data": { ... }
}
```

**실패:**
```json
{
  "success": false,
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "세션을 찾을 수 없습니다."
  }
}
```

### 에러 코드

| Code | HTTP Status | 설명 |
|------|-------------|------|
| INVALID_INPUT | 400 | 잘못된 입력 |
| POINTS_MISMATCH | 400 | 포인트 검증 실패 |
| UNAUTHORIZED | 401 | 인증 필요 |
| FORBIDDEN | 403 | 접근 권한 없음 |
| USER_NOT_FOUND | 404 | 사용자 없음 |
| SESSION_NOT_FOUND | 404 | 세션 없음 |
| RANKING_NOT_FOUND | 404 | 랭킹 정보 없음 |
| INTERNAL_ERROR | 500 | 서버 내부 오류 |

---

## Session API

### POST /api/v1/sessions

게임 세션 저장. 서버에서 포인트를 재계산하여 검증합니다.

**Headers:** `Authorization` (필수)

**Request Body:**
```json
{
  "target": "회사",
  "customTarget": null,
  "targetNickname": "상사",
  "angerBefore": 80,
  "angerAfter": 30,
  "hits": 50,
  "skillShots": 5,
  "releasedPercent": 62,
  "points": 105,
  "memo": "화풀기 완료"
}
```

**Validation:**
| 필드 | 제약 |
|------|------|
| target | @NotBlank, @Size(max=20) |
| customTarget | @Size(max=50), nullable |
| targetNickname | @NotBlank, @Size(max=50) |
| angerBefore / angerAfter | 0~100 |
| hits / skillShots | 0 이상 |
| releasedPercent | 0~100 |
| points | 0 이상 |
| memo | @Size(max=1000), nullable |

**포인트 계산 공식:**
```
points = 10 + hits + (skillShots × 4) + floor((angerBefore - min(angerAfter, angerBefore)) / 2)
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "target": "회사",
    "customTarget": null,
    "targetNickname": "상사",
    "angerBefore": 80,
    "angerAfter": 30,
    "hits": 50,
    "skillShots": 5,
    "releasedPercent": 62,
    "points": 105,
    "memo": "화풀기 완료",
    "createdAt": "2026-04-05T12:00:00"
  }
}
```

---

### GET /api/v1/sessions

내 세션 목록 조회 (페이징).

**Headers:** `Authorization` (필수)

**Query Params:**
| Param | Default | 설명 |
|-------|---------|------|
| page | 0 | 페이지 번호 |
| size | 20 | 페이지 크기 |

**Response (200):** `ApiResponse<Page<GameSessionResponse>>`

---

### GET /api/v1/sessions/{id}

세션 상세 조회. 본인 세션만 조회 가능합니다.

**Headers:** `Authorization` (필수)

**Response (200):** `ApiResponse<GameSessionResponse>`

**Error:** 다른 사용자의 세션 → `403 FORBIDDEN`

---

### PATCH /api/v1/sessions/{id}/anger-after

게임 후 분노 수치 조정. 본인 세션만 수정 가능합니다. 포인트는 변경되지 않습니다.

**Headers:** `Authorization` (필수)

**Request Body:**
```json
{
  "angerAfter": 20
}
```

**Response (200):** `ApiResponse<GameSessionResponse>`

---

## User API

### GET /api/v1/users/me

내 프로필 조회. 사용자가 없으면 자동 생성합니다.

**Headers:** `Authorization` (필수)

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "nickname": "테스트유저",
    "createdAt": "2026-04-05T10:00:00"
  }
}
```

---

### GET /api/v1/users/me/stats

내 주간/월간 통계 조회.

**Headers:** `Authorization` (필수)

**Query Params:**
| Param | Default | 설명 |
|-------|---------|------|
| period | weekly | weekly 또는 monthly |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "totalSessions": 15,
    "totalHits": 750,
    "totalPoints": 1580,
    "bestRelease": 95,
    "avgRelease": 62
  }
}
```

---

## Ranking API

### GET /api/v1/rankings/points

포인트 랭킹 조회. 인증 불요.

**Query Params:**
| Param | Default | 설명 |
|-------|---------|------|
| period | weekly | weekly, monthly, all_time |
| limit | 50 | 조회 수 |

**Response (200):**
```json
{
  "success": true,
  "data": [
    { "rank": 1, "userId": 1, "nickname": "유저1", "score": 1580.0 },
    { "rank": 2, "userId": 2, "nickname": "유저2", "score": 1200.0 }
  ]
}
```

---

### GET /api/v1/rankings/release-rate

분노 해소율 랭킹 조회. 인증 불요.

**Query Params:** `period`, `limit` (위와 동일)

---

### GET /api/v1/rankings/me

내 랭킹 순위 조회.

**Headers:** `Authorization` (필수)

**Query Params:**
| Param | Default | 설명 |
|-------|---------|------|
| criteria | points | points 또는 release |
| period | weekly | weekly, monthly, all_time |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "rank": 3,
    "totalParticipants": 150,
    "score": 1580.0
  }
}
```

---

## Home API

### GET /api/v1/home/snapshot

홈 대시보드 스냅샷. 오늘 세션 수, 최근 해소율, 주간 통계를 한 번에 조회합니다.

**Headers:** `Authorization` (필수)

**Response (200):**
```json
{
  "success": true,
  "data": {
    "todayCount": 2,
    "latestReleasePercent": 65,
    "latestTarget": "상사",
    "primaryTarget": "회사",
    "weeklySessions": 8,
    "weeklyAverageRelease": 72
  }
}
```

| 필드 | 설명 |
|------|------|
| todayCount | 오늘 세션 수 |
| latestReleasePercent | 가장 최근 세션 해소율 (0-100) |
| latestTarget | 가장 최근 대상 ("-" if 없음) |
| primaryTarget | 이번 주 가장 많이 선택한 대상 ("-" if 없음) |
| weeklySessions | 이번 주 총 세션 수 |
| weeklyAverageRelease | 이번 주 평균 해소율 (0-100) |

---

## Report API

### GET /api/v1/reports/weekly

이번 주 상세 리포트. 캘린더 뷰, 상위 대상, 가장 힘든 요일, 주간 헤드라인을 포함합니다.

**Headers:** `Authorization` (필수)

**Response (200):**
```json
{
  "success": true,
  "data": {
    "label": "4월 2주차",
    "totalSessions": 8,
    "totalHits": 450,
    "totalReleased": 380,
    "averageBefore": 72,
    "averageAfter": 35,
    "averageRelease": 72,
    "bestRelease": 95,
    "hardestWeekday": "월요일",
    "streakDays": 5,
    "weeklyHeadline": "월요일에 회사 때문에 가장 힘들었어요.",
    "topTargets": [
      { "label": "회사", "count": 4 },
      { "label": "상사", "count": 3 }
    ],
    "calendarDays": [
      {
        "dateKey": "2026-04-06",
        "dayLabel": "월",
        "dayNumber": 6,
        "angerLevel": 85,
        "sessions": [
          {
            "id": "550e8400-...",
            "target": "회사",
            "angerBefore": 80,
            "angerAfter": 30,
            "hits": 50,
            "releasedPercent": 62,
            "createdAt": "2026-04-06T12:00:00",
            "memo": "화풀기 완료"
          }
        ]
      }
    ]
  }
}
```

---

### GET /api/v1/reports/archives

지난 주 리포트 아카이브 목록 (현재 주 제외).

**Headers:** `Authorization` (필수)

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "id": "2026-03-31",
      "label": "3월 5주차",
      "periodText": "3.31 - 4.6",
      "totalSessions": 6,
      "totalHits": 320,
      "totalReleased": 210,
      "averageBefore": 68,
      "averageAfter": 40,
      "averageRelease": 68,
      "topTarget": "회사",
      "hardestWeekday": "금요일"
    }
  ]
}
```

---

## Session API (추가 엔드포인트)

### GET /api/v1/sessions/recent-targets

최근 사용한 커스텀 대상 자동완성 (최대 6개).

**Headers:** `Authorization` (필수)

**Response (200):** `ApiResponse<List<String>>`

---

### GET /api/v1/sessions/recent-nicknames

최근 사용한 닉네임 자동완성 (최대 5개).

**Headers:** `Authorization` (필수)

**Response (200):** `ApiResponse<List<String>>`

---

## Achievement API

### GET /api/v1/achievements/me

내 업적 목록 조회.

**Headers:** `Authorization` (필수)

**Response (200):**
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

---

## Streak API

### GET /api/v1/streaks/me

내 연속 플레이 스트릭 조회.

**Headers:** `Authorization` (필수)

**Response (200):**
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

---

## Target Stats API

### GET /api/v1/users/me/target-stats

내 분노 대상별 통계.

**Headers:** `Authorization` (필수)

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "target": "회사",
      "sessionCount": 25,
      "totalHits": 1250,
      "avgRelease": 72.5
    }
  ]
}
```

---

## Swagger / OpenAPI

Swagger UI에서 전체 API를 인터랙티브하게 확인할 수 있습니다.

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **API Docs (JSON):** `http://localhost:8080/api-docs`
