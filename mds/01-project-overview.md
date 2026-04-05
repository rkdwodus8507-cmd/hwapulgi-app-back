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
| Docs | Swagger/OpenAPI (springdoc-openapi) |
| Container | Docker (multi-stage build) |
| Deploy | AWS (EC2 + RDS + ElastiCache) |

## 주요 기능

- **사용자 인증** — AuthService 인터페이스로 추상화. 현재는 DevAuthService(mock)로 개발, 추후 토스 인증 연동
- **게임 세션 관리** — 대상 선택 → 타격 → 분노 수치/포인트 기록. 서버에서 포인트 재계산 검증
- **랭킹/리더보드** — Redis Sorted Set 기반 실시간 랭킹 (포인트, 해소율). 주간/월간/전체 기간별 조회
- **스케줄링** — 매주 월요일, 매월 1일에 랭킹 스냅샷을 DB에 영구 저장
- **업적/배지** — 세션 생성 시 14종 업적 자동 체크 및 배지 부여
- **연속 플레이 스트릭** — 연속 플레이 일수 계산 (현재/최고/총 플레이일)
- **대상별 통계** — 분노 대상별 세션 수/타격 수/평균 해소율 집계
- **홈 대시보드** — 오늘 세션 수, 최근 해소율, 주간 통계 스냅샷
- **주간 리포트** — 캘린더 뷰, 상위 대상, 가장 힘든 요일, 주간 헤드라인
- **자동완성** — 최근 사용한 대상/닉네임 자동완성 API

## 패키지 구조

```
com.hwapulgi.api
├── common/         공통 응답 포맷, 예외 처리, 설정 (Swagger 포함)
├── auth/           인증 인터페이스 + DevAuthService
├── user/           사용자 프로필, 통계
├── session/        게임 세션 CRUD, 포인트 계산, 대상별 통계
├── ranking/        Redis 랭킹, 스냅샷 스케줄러
├── achievement/    업적/배지 시스템
├── streak/         연속 플레이 스트릭
├── home/           홈 대시보드 스냅샷
└── report/         주간 리포트, 아카이브
```

## 프로필 설정

| Profile | DB | Redis | Auth |
|---------|------|-------|------|
| local | H2 (in-memory) | Embedded Redis (port 6370) | DevAuthService |
| dev | PostgreSQL | Redis (port 6379) | DevAuthService |
| prod | AWS (RDS) | AWS (ElastiCache) | 실제 인증 구현체 |

## 로컬 실행

```bash
# 방법 1: H2 + Embedded Redis (기본)
./gradlew bootRun

# 방법 2: Docker Compose로 인프라 띄우고 dev 프로필 사용
docker compose -f docker-compose-infra.yml up -d
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

- H2 콘솔: http://localhost:8080/h2-console
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/api-docs

## Docker 배포

```bash
# 전체 스택 (app + postgres + redis) 실행
docker compose up -d --build
```

- **Dockerfile**: multi-stage build (eclipse-temurin:21, bootJar)
- **docker-compose.yml**: app(prod) + PostgreSQL 16 + Redis 7
- **docker-compose-infra.yml**: 로컬 개발용 인프라만 (PostgreSQL + Redis)

## 테스트

```bash
./gradlew test
# 35개 테스트 (단위 14 + 통합 21)
```

## 현재 상태 & 미구현

- 실제 토스 인증 연동 — AuthService 구현체 추가 필요 (현재 prod도 DevAuthService 사용 중)
- `/api/v1/rankings/streaks` (연속 플레이 랭킹) — 추가 트래킹 로직 필요
