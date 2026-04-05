# 아키텍처

## 전체 구조

모놀리식(Monolithic) 아키텍처. 도메인별 패키지 분리로 코드 경계를 유지하며, 필요 시 모듈형 모놀리스로 전환 가능합니다.

```
Client (토스 인앱 웹뷰)
    │
    │  REST / JSON
    │  Authorization header
    ▼
┌──────────────────────────────────────────────┐
│  Spring Boot 3.5  |  Java 21                 │
│                                              │
│  Controller Layer                            │
│    SessionController                         │
│    UserController                            │
│    RankingController                         │
│         │                                    │
│  Auth Layer                                  │
│    AuthService (interface)                   │
│    └─ DevAuthService (local/dev mock)        │
│    └─ ???AuthService (prod, 토스 연동)        │
│         │                                    │
│  Service Layer                               │
│    GameSessionService ──→ RankingService      │
│    UserService            (Redis ZSet ops)   │
│    PointsCalculator                          │
│         │                                    │
│  Repository Layer (Spring Data JPA)          │
│    GameSessionRepository                     │
│    UserRepository                            │
│    RankingSnapshotRepository                 │
│         │                    │               │
└─────────┼────────────────────┼───────────────┘
          ▼                    ▼
   ┌──────────────┐    ┌─────────────┐
   │  PostgreSQL   │    │    Redis    │
   │  (prod: RDS)  │    │ (ElastiCache)│
   │  (local: H2)  │    │  Sorted Set │
   └──────────────┘    └─────────────┘
```

## 레이어별 역할

### Controller Layer

- HTTP 요청/응답 처리
- 인증 토큰 추출 → AuthService 호출
- 요청 데이터 validation (@Valid)
- 서비스 호출 후 ApiResponse 래핑

### Auth Layer

- `AuthService` 인터페이스로 인증 방식 추상화
- `DevAuthService`: `Authorization` 헤더에서 `userId:nickname` 파싱 (local/dev)
- prod 구현체: 토스 연동 방식 확정 후 추가

### Service Layer

- 비즈니스 로직 담당
- 트랜잭션 관리 (@Transactional)
- `GameSessionService`: 세션 CRUD + 포인트 검증 + 랭킹 업데이트 (Redis 실패 시 로깅 후 계속)
- `UserService`: 사용자 조회/생성 (race-condition safe: saveAndFlush + DataIntegrityViolationException catch)
- `RankingService`: Redis Sorted Set 기반 랭킹 관리
- `PointsCalculator`: 포인트 계산 공식 (서버 검증용)

### Repository Layer

- Spring Data JPA 인터페이스
- 커스텀 쿼리: 기간별 세션 조회 (JPQL), 유저 배치 조회

### Common Layer

- `ApiResponse<T>`: 통일된 응답 포맷 (success + data + error)
- `ErrorCode`: 에러 코드 enum (HTTP 상태 + 코드 + 메시지)
- `BusinessException`: 비즈니스 로직 예외
- `GlobalExceptionHandler`: 전역 예외 처리 (@RestControllerAdvice)

## 데이터 흐름

### 세션 생성 흐름

```
1. Client → POST /api/v1/sessions (Authorization, Request Body)
2. Controller: AuthService.authenticate(token) → UserInfo
3. Controller: UserService.getOrCreateUser() → User
4. Service: PointsCalculator.calculate() → 서버 포인트 검증
5. Service: GameSession 엔티티 생성 → DB 저장
6. Service: RankingService.addPoints() → Redis ZINCRBY (3개 키)
7. Service: RankingService.updateReleaseRate() → Redis (per-period best)
8. Controller: ApiResponse.ok(response) → Client
```

### 랭킹 조회 흐름

```
1. Client → GET /api/v1/rankings/points?period=weekly
2. RankingService: Redis ZREVRANGE → 상위 N명 (userId + score)
3. RankingService: UserRepository.findAllById() → 닉네임 배치 조회
4. Response: [{ rank, userId, nickname, score }, ...]
```

### 스냅샷 스케줄러

```
매주 월요일 00:00 / 매월 1일 00:00:
1. Redis에서 해당 기간 랭킹 전체 조회 (ZREVRANGE 0 -1)
2. ranking_snapshots 테이블에 저장 (user, period, points, rank)
```

## 설계 결정 사항

| 결정 | 이유 |
|------|------|
| Redis Sorted Set으로 랭킹 | 실시간 순위 조회 O(log N), 대량 사용자 대응 |
| 포인트 서버 검증 | 클라이언트 조작 방지 |
| Auth 인터페이스 추상화 | 토스 연동 방식 미정, 구현체 교체 용이 |
| getOrCreateUser race-condition 처리 | 동시 요청 시 DataIntegrityViolation catch + retry |
| 세션 GET/PATCH 소유권 체크 | IDOR 취약점 방지 |
| 랭킹 Redis 키 TTL | 주간 14일, 월간 62일 — 오래된 키 자동 정리 |
| 해소율 랭킹 per-period best | 주간/월간/전체 각각 독립적으로 최고 기록 관리 |
| embedded-redis를 testImplementation | 프로덕션 JAR에서 불필요한 바이너리 제거 |

## 아키텍처 다이어그램

이미지 파일: `hwapulgi-architecture.png` (Downloads 폴더)
