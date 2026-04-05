# Hwapulgi Backend - 프로젝트 개요

## 소개

화풀기(Hwapulgi)는 분노 해소 게임 앱의 백엔드 서버입니다. 토스 인앱 웹뷰에서 동작하는 프론트엔드와 연동하여, 사용자가 분노를 게임으로 해소하고 그 결과를 저장/랭킹하는 서비스를 제공합니다.

## 기술 스택

| 항목 | 선택 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Build | Gradle (Kotlin DSL) |
| DB | PostgreSQL (prod) / H2 (local) |
| Cache | Redis — 랭킹 Sorted Set |
| Auth | 인터페이스 추상화 (토스 연동 예정) |
| API | REST (JSON) |
| Deploy | AWS (EC2 + RDS + ElastiCache) |

## 주요 기능

- **사용자 인증** — AuthService 인터페이스로 추상화. 현재는 DevAuthService(mock)로 개발, 추후 토스 인증 연동
- **게임 세션 관리** — 대상 선택 → 타격 → 분노 수치/포인트 기록. 서버에서 포인트 재계산 검증
- **랭킹/리더보드** — Redis Sorted Set 기반 실시간 랭킹 (포인트, 해소율). 주간/월간/전체 기간별 조회
- **스케줄링** — 매주 월요일, 매월 1일에 랭킹 스냅샷을 DB에 영구 저장

## 패키지 구조

```
com.hwapulgi.api
├── common/         공통 응답 포맷, 예외 처리, 설정
├── auth/           인증 인터페이스 + DevAuthService
├── user/           사용자 프로필, 통계
├── session/        게임 세션 CRUD, 포인트 계산
└── ranking/        Redis 랭킹, 스냅샷 스케줄러
```

## 프로필 설정

| Profile | DB | Redis | Auth |
|---------|------|-------|------|
| local | H2 (in-memory) | Embedded Redis (port 6370) | DevAuthService |
| dev | PostgreSQL | Redis (port 6379) | DevAuthService |
| prod | AWS RDS | AWS ElastiCache | 실제 인증 구현체 |

## 로컬 실행

```bash
./gradlew bootRun
# 기본 프로필: local (H2 + Embedded Redis)
# H2 콘솔: http://localhost:8080/h2-console
```

## 테스트

```bash
./gradlew test
# 19개 테스트 (단위 10 + 통합 6 + 기타 3)
```

## 현재 상태 & 미구현

- `/api/v1/rankings/streaks` (연속 플레이 랭킹) — 추가 트래킹 로직 필요
- 실제 토스 인증 연동 — AuthService 구현체 추가 필요
- prod 프로필 설정 (application-prod.properties)
