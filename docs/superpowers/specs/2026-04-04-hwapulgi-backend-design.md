# Hwapulgi App Backend Design Spec

## Overview

화풀기(Hwapulgi) 분노 해소 게임 앱의 백엔드 서버. 토스 인앱 웹뷰로 동작하는 프론트엔드와 연동하여 사용자 인증, 게임 세션 저장, 랭킹/리더보드 기능을 제공한다.

## Tech Stack

| 항목 | 선택 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build | Gradle (Kotlin DSL) |
| DB | PostgreSQL (Spring Data JPA) |
| Cache | Redis (랭킹 Sorted Set) |
| Auth | 추후 결정 (토스 인앱 연동, 인터페이스로 추상화) |
| API | REST (JSON) |
| Deploy | AWS (EC2 + RDS + ElastiCache) |

## Architecture

모놀리식(Monolithic) 아키텍처. 도메인별 패키지 분리로 코드 경계를 유지하며, 필요 시 모듈형 모놀리스로 전환 가능.

## Package Structure

```
com.hwapulgi.api
├── auth/           # 인증 (추후 토스 연동)
│   ├── controller/
│   ├── service/
│   └── dto/
├── user/           # 사용자 프로필, 통계
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── dto/
├── session/        # 게임 세션 CRUD
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── dto/
├── ranking/        # 랭킹/리더보드
│   ├── controller/
│   ├── service/
│   └── dto/
├── common/         # 공통 (예외 처리, 응답 포맷, 설정)
│   ├── config/
│   ├── exception/
│   └── response/
└── HwapulgiApplication.java
```

## Data Model

### users

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, auto) | 내부 사용자 ID |
| external_id | VARCHAR | 외부 인증 식별자 (토스 연동 시 사용) |
| nickname | VARCHAR | 사용자 닉네임 |
| created_at | TIMESTAMP | 가입일 |
| updated_at | TIMESTAMP | 수정일 |

### game_sessions

| Column | Type | Description |
|--------|------|-------------|
| id | UUID (PK) | 세션 ID |
| user_id | BIGINT (FK → users) | 사용자 |
| target | VARCHAR | 대상 (회사, 상사, 동료, 고객, 가족, 친구, 연인, 나 자신, 기타) |
| custom_target | VARCHAR (nullable) | 기타 선택 시 커스텀 대상 |
| target_nickname | VARCHAR | 대상 닉네임 |
| anger_before | INTEGER | 게임 전 분노 수치 (0-100) |
| anger_after | INTEGER | 게임 후 분노 수치 (0-100) |
| hits | INTEGER | 총 타격 수 |
| skill_shots | INTEGER | 스킬샷 수 |
| released_percent | INTEGER | 분노 해소율 (%) |
| points | INTEGER | 획득 포인트 |
| memo | TEXT (nullable) | 메모 |
| created_at | TIMESTAMP | 세션 생성일 |

### ranking_snapshots

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, auto) | 스냅샷 ID |
| user_id | BIGINT (FK → users) | 사용자 |
| period_type | ENUM | WEEKLY, MONTHLY, ALL_TIME |
| period_key | VARCHAR | 기간 키 (예: "2026-W14", "2026-04") |
| total_points | INTEGER | 기간 내 총 포인트 |
| total_sessions | INTEGER | 기간 내 총 세션 수 |
| total_hits | INTEGER | 기간 내 총 타격 수 |
| best_release | INTEGER | 최고 해소율 (%) |
| avg_release | INTEGER | 평균 해소율 (%) |
| rank | INTEGER | 해당 기간 순위 |
| updated_at | TIMESTAMP | 마지막 업데이트 |

## API Endpoints

### Auth (추후 구체화)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/auth/login` | 로그인 |
| POST | `/api/v1/auth/logout` | 로그아웃 |

### Session

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/sessions` | 게임 세션 저장 |
| GET | `/api/v1/sessions` | 내 세션 목록 조회 (페이징) |
| GET | `/api/v1/sessions/{id}` | 세션 상세 조회 |
| PATCH | `/api/v1/sessions/{id}/anger-after` | 분노 수치 조정 |

### User

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/users/me` | 내 프로필 조회 |
| GET | `/api/v1/users/me/stats` | 내 주간/월간 통계 |

### Ranking

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/rankings/points` | 포인트 랭킹 (query: period=weekly/monthly/all) |
| GET | `/api/v1/rankings/release-rate` | 분노 해소율 랭킹 |
| GET | `/api/v1/rankings/streaks` | 연속 플레이 랭킹 |
| GET | `/api/v1/rankings/me` | 내 랭킹 순위 조회 |

## Common Response Format

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

Error case:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "세션을 찾을 수 없습니다."
  }
}
```

## Ranking System

- **Redis Sorted Set**으로 실시간 랭킹 관리
- 키 구조: `ranking:{criteria}:{period}:{period_key}` (예: `ranking:points:weekly:2026-W14`)
- 게임 세션 저장 시 → Redis 점수 업데이트 (`ZINCRBY`)
- 랭킹 조회 → Redis `ZREVRANGE` / `ZREVRANK`
- `@Scheduled` 스케줄러: 매주 월요일 00:00, 매월 1일 00:00에 `ranking_snapshots` 테이블로 스냅샷 저장

## Auth Abstraction

인증 방식이 미정이므로 인터페이스로 추상화:

```java
public interface AuthService {
    UserInfo authenticate(String token);
}
```

- `DevAuthService`: 개발용 mock 구현체 (헤더에서 userId 직접 전달)
- 토스 연동 방식 확정 후 실제 구현체 추가

## Profiles

| Profile | DB | Redis | Auth |
|---------|------|-------|------|
| local | H2 (in-memory) | Embedded Redis | DevAuthService |
| dev | PostgreSQL (dev) | Redis (dev) | DevAuthService |
| prod | RDS PostgreSQL | ElastiCache Redis | 실제 인증 구현체 |

## Points Calculation

프론트엔드와 동일한 공식을 서버에서도 검증:

```
points = 10 + hits + (skillShots × 4) + floor((angerBefore - min(angerAfter, angerBefore)) / 2)
```

세션 저장 시 클라이언트가 보낸 points를 서버에서 재계산하여 검증한다.