# REST API 명세 초안

API 버전(`/api/v1`) 적용 여부, 공통 페이지네이션과 세부 DTO 필드는 TODO이다. 민감정보는 역할별 응답 DTO로 제한한다.

## 공통 정책

- 인증된 사용자만 호출하며 표의 역할과 리소스 소유권·담당 부서를 모두 검증한다.
- 쓰기 API는 성공 시 변경된 리소스 ID와 상태를 반환한다.
- `400`은 형식·필드 오류, `403`은 권한 부족, `404`는 조회 가능한 대상 없음, `409`는 상태·동시성·중복 충돌에 사용한다.
- 상태 변경은 범용 상태 수정 API가 아니라 업무 명령 API로만 수행한다.

## 상품 API

| Method / URI | 설명·역할 | Request | Response | 주요 Validation | 실패 응답 | 트랜잭션 |
|---|---|---|---|---|---|---|
| `GET /api/products` | 신청 가능 상품 목록 / 인증 사용자 | 기간·페이지 조건 | 상품 요약 목록 | 기간 형식, 활성 상품 | `400` | 읽기 전용 |
| `GET /api/products/{id}` | 상품과 현재 규칙 / 인증 사용자 | Path: 상품 ID | 상품·규칙 요약 | ID 및 조회 가능 기간 | `400`, `404` | 읽기 전용 |
| `POST /api/products` | 상품 생성 / ADMIN | 상품 코드·명·한도·기간 | 상품 ID·상태 | 코드 UNIQUE, 금액 양수, 기간 | `400`, `403`, `409` | 필수 |
| `PUT /api/products/{id}` | 상품 변경 및 규칙 버전 추가 / ADMIN | 변경값·현재 버전 | 상품 ID·새 버전 | 낙관적 락, 유효기간 중복 | `400`, `403`, `404`, `409` | 필수 |

## 신청·심사 API

| Method / URI | 설명·역할 | Request | Response | 주요 Validation | 실패 응답 | 트랜잭션 |
|---|---|---|---|---|---|---|
| `POST /api/applications` | 신청 초안 생성 / CUSTOMER | 상품 ID·신청 기본정보 | 신청 ID·`DRAFT` | 본인, 상품 존재·활성 | `400`, `403`, `404` | 필수 |
| `POST /api/applications/{id}/documents` | 증빙 등록 / CUSTOMER | 문서 종류·파일 또는 저장키 | 문서 ID·검증 상태 | 신청 소유권, 상태, 형식·크기 | `400`, `403`, `404`, `409` | 필수(TODO: 파일 저장 경계) |
| `POST /api/applications/{id}/submit` | 접수와 자격검증 / CUSTOMER | 현재 버전·제출 확인 | 상태·규칙 버전·검증 결과 | 필수값, 문서, 중복신청, `DRAFT` | `400`, `403`, `404`, `409` | 필수 |
| `GET /api/applications/{id}` | 신청 상세 / CUSTOMER·직원 | Path: 신청 ID | 역할별 신청 상세 | 본인 또는 담당 부서 | `403`, `404` | 읽기 전용 |
| `POST /api/applications/{id}/assignments` | 최초 배정·재배정 / REVIEWER 또는 ADMIN(TODO) | 부서·직원 ID·배정 사유 | 배정 ID·현재 담당자·배정일시 | 권한, 직원 활성·소속 부서, 기존 활성 배정 | `400`, `403`, `404`, `409` | 필수: 기존 활성 배정 해제와 신규 배정 생성을 원자적으로 처리 |
| `GET /api/applications/{id}/assignments/current` | 현재 담당자 조회 / 담당 직원·ADMIN·AUDITOR | Path: 신청 ID | 배정 ID·담당 부서·담당 직원·배정일시 | 신청 접근 범위, 활성 배정 존재 | `403`, `404` | 읽기 전용 |
| `GET /api/applications/{id}/assignments` | 전체 배정·재배정 이력 / REVIEWER·ADMIN·AUDITOR | Path: 신청 ID·페이지 조건 | 배정자·배정/해제 시각을 포함한 이력 | 부서·감사 권한, 페이지 제한 | `400`, `403`, `404` | 읽기 전용 |
| `DELETE /api/applications/{id}/assignments/current` | 현재 배정 해제 / REVIEWER 또는 ADMIN(TODO) | 해제 사유 | 해제된 배정 ID·해제일시 | 권한, 활성 배정 존재, 신청 상태 | `400`, `403`, `404`, `409` | 필수 |
| `POST /api/applications/{id}/screenings` | 심사 결과 등록 / REVIEWER | 결과·의견·근거 | 심사 ID·신청 상태 | 담당자, `UNDER_REVIEW` | `400`, `403`, `404`, `409` | 필수 |
| `POST /api/applications/{id}/supplements` | 보완 요청 / REVIEWER | 보완 항목·사유·기한 | 요청 ID·`SUPPLEMENT_REQUESTED` | 담당자, 상태, 미래 기한 | `400`, `403`, `404`, `409` | 필수 |
| `POST /api/applications/{id}/decision` | 승인 또는 거절 / REVIEWER | 결정·사유·승인금액 | 심사 ID·변경 상태 | 담당자, `UNDER_REVIEW`, 승인한도 | `400`, `403`, `404`, `409` | 필수 |

## 대출·상환 API

| Method / URI | 설명·역할 | Request | Response | 주요 Validation | 실패 응답 | 트랜잭션 |
|---|---|---|---|---|---|---|
| `POST /api/loans` | 승인 신청 대출 실행 / POST_MANAGER | 신청 ID·실행일·계좌 참조, `Idempotency-Key` | 대출 ID·실행 거래 ID·상태 | `APPROVED`, 한도, 계좌, 멱등성 | `400`, `403`, `404`, `409` | 필수 |
| `GET /api/loans/{id}` | 계약과 현재 잔액 / CUSTOMER·직원 | Path: 대출 ID | 역할별 계약 요약 | 본인 또는 담당 부서 | `403`, `404` | 읽기 전용 |
| `GET /api/loans/{id}/schedules` | 상환계획 / CUSTOMER·직원 | Path: 대출 ID | 회차별 납부계획 | 본인 또는 담당 부서 | `403`, `404` | 읽기 전용 |
| `POST /api/loans/{id}/repayments` | 상환 처리 / CUSTOMER·POST_MANAGER | 금액·납부일, `Idempotency-Key` | 거래 ID·잔액·재사용 여부 | 금액 양수·잔액 이하, 멱등키·본문 해시 | `400`, `403`, `404`, `409` | 필수 |
| `GET /api/loans/{id}/transactions` | 금융거래 원장 / CUSTOMER·직원 | 기간·커서·페이지 크기 | 거래 목록·다음 커서 | 접근 범위, 기간·페이지 제한 | `400`, `403`, `404` | 읽기 전용 |

`Idempotency-Key`의 길이, 유효기간과 요청 본문 정규화·해시 방식은 TODO이다. 같은 키와 다른 본문은 `409 IDEMPOTENCY_KEY_REUSED`로 거절한다.

## 감사·운영 API

| Method / URI | 설명·역할 | Request | Response | 주요 Validation | 실패 응답 | 트랜잭션 |
|---|---|---|---|---|---|---|
| `GET /api/audit-logs` | 감사로그 검색 / AUDITOR | 목적·기간·행위자·대상·커서 | 마스킹된 로그 목록 | 조회 목적, 기간 상한, 감사 권한 | `400`, `403` | 읽기 전용 + 조회 감사기록 |
| `GET /api/overdues` | 연체 대상 / POST_MANAGER | 상태·기준일·부서·커서 | 연체 대출 요약 | 담당 부서, 날짜·페이지 | `400`, `403` | 읽기 전용 |
| `GET /api/risk-alerts` | 이상경보 / POST_MANAGER·AUDITOR | 규칙·상태·기간, 고객·신청·대출·거래 FK 필터, 커서 | 규칙·판단 근거와 명시적 대상 참조 목록 | 역할별 범위, 대상 FK 유효성 | `400`, `403` | 읽기 전용 |
| `GET /api/dashboard/summary` | 비식별 운영 집계 / ADMIN·POST_MANAGER | 기간·상품·지역 | 집계 지표 | 기간 상한, 최소 집단 크기 | `400`, `403` | 읽기 전용 |

## 공통 오류 응답

```json
{
  "code": "INVALID_STATE_TRANSITION",
  "message": "현재 상태에서는 요청을 처리할 수 없습니다.",
  "traceId": "TODO",
  "fieldErrors": [
    { "field": "decision", "reason": "허용되지 않은 값입니다." }
  ]
}
```

내부 SQL, 스택 트레이스, 개인정보와 권한상 조회할 수 없는 대상의 존재 여부는 응답에 노출하지 않는다.
