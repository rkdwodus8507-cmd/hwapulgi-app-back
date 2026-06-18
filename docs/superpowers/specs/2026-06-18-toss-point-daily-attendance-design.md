# 토스포인트 일일 출석 적립 — 설계

작성일: 2026-06-18
브랜치: `feat/toss-point-daily-attendance`

## 목적

유저가 하루 1번 "포인트 받기" 버튼을 눌러 토스포인트를 적립받는 출석 보상 기능. 리텐션 강화가 목적.

토스 프로모션 정책상 **고정 금액 지급만** 가능(점수 비례 불가), **1인당 누적 5,000P 미만**, 확률/룰렛/추첨 불가.

## 확정된 제품 결정

- **지급 트리거**: 출석/일일 미션
- **수령 방식**: 수동 수령 버튼 (프론트가 명시적으로 claim 호출)
- **수령 조건**: 순수 출석 (게임 플레이 등 선행 조건 없음)
- **지급 주기**: 하루 1회 (KST, `Asia/Seoul` 자정 경계)

## 전체 흐름

```
프론트 ──POST /api/v1/promotion/daily/claim (JWT)──▶ 백엔드
  1) 오늘(KST) 이미 받았나? DB 확인 → 받았으면 409 ALREADY_CLAIMED
  2) 지급 슬롯 선점: daily_point_grant INSERT (status=REQUESTED, unique(userId, grantDate))
  3) 토스 호출: get-key → execute-promotion → execution-result  (mTLS + x-toss-user-key)
  4) 성공 → status=COMPLETED 저장 / 실패 → status=FAILED
  5) 적립 금액 응답
```

**핵심 동시성 결정**: `unique(userId, grantDate)` DB 제약으로 따닥 클릭·동시 요청을 DB 레벨에서 차단한다. 토스 key는 1회용이라 멱등성도 자연히 보장된다. 슬롯을 먼저 INSERT(REQUESTED)한 뒤 토스를 호출하므로, 동시 요청 중 하나만 제약을 통과한다.

## 컴포넌트 (기존 패턴 준수, 신규 추가)

| 신규 | 역할 | 의존 |
|------|------|------|
| `PromotionClient` | 토스 프로모션 3개 엔드포인트 호출/응답 파싱 | 기존 mTLS `RestTemplate`(`appsInTossRestTemplate`), `appsInTossBaseUrl` 재사용 |
| `DailyPointGrant` (엔티티) | 적립 원장 | — |
| `DailyPointGrantRepository` | 원장 조회/저장 | Spring Data JPA |
| `PromotionService` | 하루1회 검증 + 원장 관리 + 토스 호출 오케스트레이션 | 위 셋, `UserRepository` |
| `PromotionController` | claim / status 엔드포인트 | `PromotionService`, JWT 인증 |
| 설정값 | `appintoss.promotion.daily.code`, `appintoss.promotion.daily.amount` | application.properties |

각 단위는 단일 책임을 가지며 인터페이스로 통신한다: `PromotionClient`는 HTTP 세부를 캡슐화하고, `PromotionService`는 비즈니스 규칙(하루1회·원장)을 담당하며, `PromotionController`는 입출력 변환만 한다.

## 데이터 모델

`DailyPointGrant`

| 필드 | 타입 | 비고 |
|------|------|------|
| id | Long | PK |
| userId | Long | 우리 User PK |
| grantDate | LocalDate | KST 기준 날짜 |
| promotionCode | String | 콘솔에서 등록한 프로모션 코드 |
| amount | int | 지급 포인트 |
| tossKey | String | 토스 get-key 응답 키 (nullable, 발급 후 채움) |
| status | enum | `REQUESTED` / `COMPLETED` / `FAILED` |
| createdAt | Instant | 생성 시각 |

제약: `UNIQUE (userId, grantDate)`

## API

### `POST /api/v1/promotion/daily/claim`
- 인증: JWT (access token) → userId 추출
- 성공 200: `{ "granted": true, "amount": 100, "grantedAt": "2026-06-18T09:00:00+09:00" }`
- 이미 수령 409: `ALREADY_CLAIMED`
- 토스 실패 시: 5xx 계열 비즈니스 에러 (원장 FAILED 기록)

### `GET /api/v1/promotion/daily/status`
- 인증: JWT
- 200: `{ "claimable": true, "amount": 100, "lastClaimedAt": "2026-06-17T09:00:00+09:00" }`
- 버튼 활성/비활성 표시에 사용

## 토스 API 매핑

기준 문서: https://developers-apps-in-toss.toss.im/promotion/develop.html
도메인: `https://apps-in-toss-api.toss.im` (기존 `appsInTossBaseUrl`)

1. `POST /api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key` → 1시간 유효 key 발급
2. `POST /api-partner/v1/apps-in-toss/promotion/execute-promotion` `{promotionCode, key, amount}` → 적립 실행
3. `POST /api-partner/v1/apps-in-toss/promotion/execution-result` `{promotionCode, key}` → 결과 확인

- 헤더: `x-toss-user-key` = 유저의 `externalId` (= 토스 userKey, 토스 로그인 시 이미 저장됨)
- 인증: mTLS (이미 `AppsInTossConfig`에 설정됨)
- **미확정**: 콘솔 API 키가 별도 인증 헤더로 필요한지 여부. 로그인 스모크 테스트(mTLS 단독으로 인증되는지)로 확정한다. 필요할 경우 `PromotionClient`에 헤더 한 줄과 설정값 하나만 추가하면 되도록 격리한다.

## 에러/엣지 처리

- 토스 `4113`(이미 지급) → 원장과 동기화하여 COMPLETED 처리
- 토스 `4114`(1회 지급 한도 초과) → 설정 금액 점검 필요 에러로 변환
- 2단계 성공 후 DB 갱신 실패 등 부분 실패 → status로 추적, `execution-result` 재조회로 복구 가능
- KST 자정 경계로 "하루" 판정 (`Asia/Seoul`)
- 누적 5,000P 미만 정책: 일일 금액 설정이 정책 범위 내인지 가드

## 테스트 (TDD)

- `PromotionService`
  - 첫 수령 성공 → COMPLETED 원장 1건
  - 같은 날 재수령 차단 → 409, 토스 미호출
  - 날짜 변경 후 재수령 가능
  - 토스 호출 실패 → status FAILED, 사용자에게 에러
- `PromotionClient`
  - 정상 응답 파싱, 에러코드(4113/4114) 매핑 (MockRestServiceServer)
- 통합
  - claim 200/409, status 정확도 (claimable 토글)

## 선행 조건 (운영)

- 앱인토스 콘솔에서 일일출석용 **promotionCode 생성**
- **Biz Wallet 예산 충전** (충전 전엔 실제 지급 안 됨, 코드는 사전 작성 가능)

## 범위 밖 (YAGNI)

- 연속 출석(스트릭) 보너스, 게임 플레이 선행 조건, 자동 지급 — 이번 범위 아님
- 다중 프로모션 코드 관리 UI — 단일 일일출석 코드만
