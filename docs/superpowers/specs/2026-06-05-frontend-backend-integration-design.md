# Frontend ↔ Backend Integration Design

## Overview

화풀기(Hwapulgi) 앱의 프론트엔드를 `localStorage` 기반 단일 사용자 동작에서 백엔드 API 호출 기반 멀티 사용자 동작으로 전환한다. 게임 세션 저장, 홈 스냅샷, 주간 리포트, 랭킹 등 모든 데이터 영역이 대상.

본 스펙은 **사업자 등록 / 토스 로그인 활성화 이전** 단계의 통합 작업만을 다룬다. 토스 로그인은 추후 별도 스펙에서 다루며, 본 작업에서 도입하는 인증 추상화 위에 추가로 끼우는 형태가 된다.

## Goals

- 프론트의 모든 데이터 영역(세션·홈·리포트·랭킹·통계·업적·스트릭)을 백엔드 API로 전환
- 사업자 등록 전에도 동작 가능한 임시 인증(디바이스 게스트 ID) 도입
- 백엔드 인증을 안전한 자체 JWT 기반으로 통합 (`DevAuthService` 평문 토큰 폐기)
- React Query 도입으로 서버 상태/캐싱/재시도/Invalidation 일관 처리

## Non-Goals

- 토스 로그인 실연동 (Client ID 발급 이후 별도 스펙)
- 인앱 결제, 앱푸시, 토스 포인트 적립
- 기존 localStorage 사용자 데이터 마이그레이션 (현재 실사용자 없음 전제로 폐기)
- 오프라인 큐 / 자동 백그라운드 동기화

## Architecture

```
[Toss WebView]
      │
      ▼
[Frontend: hwapulgi_app_front]
  • React Query (서버 상태 관리)
  • lib/api/* (도메인별 fetch 모듈)
  • lib/api/auth.ts (게스트 ID + 토큰 관리)
  • AppState.tsx (순수 UI 상태만)
      │
      ▼ HTTPS, Authorization: Bearer <JWT>
[Backend: hwapulgi-app-back @ 168.107.62.13.nip.io]
  • /api/auth/guest/*   (NEW)
  • /api/v1/*           (기존)
  • JwtAuthFilter       (NEW, DevAuthService 대체)
```

## Backend Changes

### 1. 게스트 인증 엔드포인트 신설

```
POST /api/auth/guest/login
Request:  { "deviceId": "<UUID v4>" }
Response: {
  "data": {
    "userId": 123,
    "nickname": "게스트123",
    "accessToken": "<JWT>",
    "refreshToken": "<JWT>",
    "expiresIn": 3600
  }
}
```

**동작:** `users.device_id`로 조회 → 존재하면 로그인, 없으면 신규 생성 후 로그인.
**닉네임:** 신규 가입 시 `"게스트" + userId` 형태로 자동 부여, 추후 사용자 변경 허용 여부는 본 스펙 범위 외.

### 2. `users` 테이블 스키마 변경

| Column | Type | Description |
|--------|------|-------------|
| device_id | VARCHAR(36) UNIQUE NULL | 게스트 가입 시 클라가 생성한 UUID |

기존 `external_id`(토스 연동용)는 그대로 유지. 추후 토스 로그인 풀리면 한 user row가 `device_id`와 `external_id`를 모두 가질 수 있음 (게스트→토스 머지 시).

### 3. 자체 JWT 도입 (`AuthService` 구현체 교체)

`DevAuthService`(`{userId}:{nickname}` 평문 파싱)는 프로덕션 위험이 명백함. 교체.

- 신규: `JwtAuthService implements AuthService`
- 알고리즘: HMAC SHA-256
- Secret: 환경변수 `JWT_SECRET` (32바이트 이상, GitHub Actions Secret 주입)
- accessToken: 만료 1시간, claims `{ sub: userId, nickname }`
- refreshToken: 만료 30일, claims `{ sub: userId, type: "refresh" }`
- 게스트·토스 둘 다 같은 JWT 발급 → 인증 코드 일원화
- `TossAuthService.login()` 및 `refresh()` 둘 다 토스 access_token 대신 자체 JWT(access/refresh) 발급으로 변경. 토스 access_token은 백엔드 내부에서 `getLoginMe()` 호출까지만 사용하고 외부 노출하지 않음. `unlink()`는 자체 JWT에서 userId 추출 후 처리.

### 4. `DevAuthService` 제거

`@Profile({"local", "dev", "prod"})`에서 `JwtAuthService`로 완전 교체. 단순 평문 토큰을 프로덕션에 두는 것은 보안 사고 직결.

### 5. 환경변수 추가

```
JWT_SECRET=<32바이트 이상 랜덤>
APPINTOSS_API_KEY=<콘솔에서 발급받은 키>  # 토스 API 추가 인증 필요 시
```

`application.properties`(base 파일)에서 `${JWT_SECRET}`, `${APPINTOSS_API_KEY:}`로 참조하여 모든 프로파일에 공통 적용. 프로파일별 오버라이드 불필요.
`.env.local`(gitignored), GitHub Actions Secret, 프로덕션 서버 env로 주입.

## Frontend Changes

### 1. 디렉토리 구조

```
src/lib/
  api/
    client.ts         # fetch 래퍼: BaseURL, Bearer 자동, 401→refresh, ApiError 표준화
    auth.ts           # guestLogin, refresh, getValidToken (기존 lib/auth.ts 흡수)
    sessions.ts       # createSession, listSessions, updateAngerAfter, recentTargets, recentNicknames
    home.ts           # getHomeSnapshot
    report.ts         # getWeeklyReport, getArchives
    ranking.ts        # getPointsRanking, getReleaseRanking, getMyRanking
    achievement.ts    # getMyAchievements
    streak.ts         # getMyStreak
    user.ts           # getMe, getMyStats, getMyTargetStats
  queries/
    keys.ts           # queryKey 팩토리 (invalidation 신뢰 가능하도록)
    sessions.ts       # useSessions, useCreateSession 등 React Query 훅
    home.ts, report.ts, ranking.ts, achievement.ts, streak.ts, user.ts
  leaderboard.ts      # 유지 (토스 SDK 직접 호출)
  haptics.ts, sounds.ts, sanitize.ts, ad.ts  # 유지
  storage.ts          # ← 전체 삭제
```

### 2. 인증 흐름

```
앱 진입 (App.tsx mount)
  ↓
localStorage 'hwapulgi/deviceId' 확인
  없으면 crypto.randomUUID() 생성 후 저장
  ↓
POST /api/auth/guest/login { deviceId }
  ↓
accessToken / refreshToken → localStorage 'hwapulgi/auth'
  ↓
이후 모든 API 호출에 Authorization: Bearer <accessToken> 자동 첨부
  ↓
401 응답 시:
  1) refreshToken으로 갱신 1회 시도
  2) 실패 시 deviceId로 게스트 재로그인 1회 시도
  3) 그래도 실패면 ApiError throw → 에러 토스트 표시
```

### 3. React Query 설정

- 패키지: `@tanstack/react-query` ^5.x
- `App.tsx`에 `QueryClientProvider` 추가
- defaultOptions:
  - `staleTime: 30_000` (30초)
  - `retry: 1`
  - `refetchOnWindowFocus: false` (앱인토스 환경)
- Mutation은 `onSuccess`에서 관련 query invalidate
  - 예: `useCreateSession` → invalidate `sessions`, `home`, `report.weekly`

### 4. 화면별 마이그레이션 매핑

| 화면 | 현재 (localStorage) | 변경 후 (API) |
|------|---------------------|---------------|
| 홈 | `getHomeSnapshot(loadSessions(), weeklySummary)` | `useQuery(home.snapshot)` → `GET /api/v1/home/snapshot` |
| 인트로 | `INTRO_SEEN_STORAGE_KEY` | localStorage 유지 (UI 플래그) |
| 시작 (타겟/닉네임 선택) | `recentCustomTargets/Nicknames` 클라 계산 | `useQuery(sessions.recentTargets)`, `useQuery(sessions.recentNicknames)` |
| 게임 | (변경 없음) | (변경 없음) |
| 결과 | `saveSession`, `updateSession` | `useMutation(sessions.create)`, `useMutation(sessions.updateAngerAfter)` |
| 리포트 | `getWeeklySummary`, `getWeeklyArchives` | `useQuery(report.weekly)`, `useQuery(report.archives)` |

### 5. `AppState.tsx` 슬림화

남는 책임: UI 폼 상태(`draft`), `setTarget/setNickname/...`, `introSeen`, `lastResult`(결과 화면 표시용 1회성).
제거: `sessions`, `weeklySummary`, `weeklySummaries`, `weeklyArchives`, `homeSnapshot`, `recentCustomTargets`, `recentNicknames`, `hasHistory`, `completeSession`, `updateLastResultAngerAfter`, `reuseSessionDraft`.

위 제거 항목들은 각 화면에서 직접 React Query 훅으로 가져옴.

### 6. 삭제할 코드

- `src/lib/storage.ts` 전체
- `src/constants.ts`의 `STORAGE_KEY` (단, `INTRO_SEEN_STORAGE_KEY`는 유지)
- `src/lib/auth.ts` **파일 자체** (rename + 내용 갱신 형태로 `src/lib/api/auth.ts`로 통째로 이동, stale 파일이 남지 않도록 함). 내부의 throw-stub `tossLogin()`은 게스트 인증으로 대체.

## Error Handling

- `api/client.ts`에서 표준 에러 throw: `ApiError { status, code, message }`
- React Query `onError`에서 토스트 표시 (`react-hot-toast` 등 가벼운 라이브러리 1개 추가)
- 화면에서는 `isError && <ErrorPanel onRetry={refetch} />`로 표시
- 결과 저장 mutation 실패 시: 결과 화면에 "저장 실패, 다시 시도" 버튼

## Testing Strategy

### 백엔드
- 단위: `GuestAuthService` (신규/기존 사용자, 동일 deviceId 재로그인)
- 단위: `JwtAuthService` (발급/검증/만료/잘못된 서명)
- 통합: `@SpringBootTest`로 `/api/auth/guest/login` → 발급된 토큰으로 보호된 엔드포인트 호출까지 한 번에

### 프론트
- 핵심 훅(`useCreateSession`, `useHomeSnapshot`, `useGuestLogin`) MSW로 모킹 테스트
- `api/client.ts`의 401 자동 갱신 시나리오 테스트

### 수동 E2E
- dev 환경 배포 후: 게스트 첫 로그인 → 게임 1판 → 결과 저장 → 홈/리포트 새로고침 시 반영 확인
- 토큰 만료 시뮬레이션 (`accessToken` 강제 만료) → 자동 갱신 확인
- 네트워크 차단 → 에러 토스트 + 재시도 동작 확인

## Implementation Order (Dependency-Driven)

1. **백엔드 PR-1**: `users.device_id` 컬럼 + 게스트 인증 엔드포인트 + `JwtAuthService` 도입 + `DevAuthService` 제거
2. **백엔드 PR-2**: `TossAuthService`도 JWT 발급으로 전환 (사업자 등록 전이라 자유롭게 변경 가능)
3. **프론트 PR-1**: React Query 설치 + `lib/api/client.ts` + 게스트 인증
4. **프론트 PR-2**: 도메인별 API/Query 모듈 (`sessions`, `home`, `report`, ...)
5. **프론트 PR-3**: 화면 마이그레이션 (홈/시작/결과/리포트)
6. **프론트 PR-4**: `storage.ts` 제거, `AppState.tsx` 슬림화
7. **검증**: dev 환경 배포 후 수동 E2E

## Risks & Mitigations

| 리스크 | 영향 | 완화 |
|--------|------|------|
| JWT secret 노출 | 인증 우회 | 환경변수만 사용, GitHub Actions Secret, 32바이트+ |
| 401 무한 루프 (refresh 실패→재로그인 실패→401) | 앱 행 | refresh 1회 + 게스트 재로그인 1회로 제한, 그 후엔 에러 throw |
| 토스 로그인 풀린 후 게스트→토스 머지 정책 미정 | 추후 결정 미룸 | 본 스펙 외. `device_id`와 `external_id`가 한 user에 공존 가능하도록 스키마만 준비 |
| React Query 학습 곡선 | 도입 후 일관성 깨질 가능성 | `queries/keys.ts` 팩토리 강제, 코드 리뷰에서 패턴 확인 |
| `crypto.randomUUID()`는 HTTPS(혹은 localhost) 컨텍스트에서만 동작 | 게스트 ID 생성 실패 | dev 도메인은 `nip.io`로 HTTPS, 프로덕션은 토스 WebView이므로 문제 없음. QA 시 한 번 확인. |
