# 테이블 명세 초안

공통 기준은 `BIGINT GENERATED ... AS IDENTITY`, 시간은 `TIMESTAMPTZ`, 금액은 `NUMERIC(19,2)`를 우선 제안한다. 개인정보 길이·암호화 방식과 코드 값은 TODO이다.

## CUSTOMER

목적: 고객과 최소 식별정보 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| customer_id | BIGINT | N | PK | IDENTITY | 고객 ID |
| customer_no | VARCHAR(30) | N | - | UNIQUE | 업무 고객번호 |
| name_enc | BYTEA | N | - | - | 암호화 이름(TODO) |
| phone_enc | BYTEA | N | - | - | 암호화 전화번호 |
| created_at | TIMESTAMPTZ | N | - | - | 생성 시각 |

인덱스: `UK(customer_no)`, 검색용 해시 인덱스 TODO.  
삭제 정책: 논리 삭제 또는 비식별화; 보존 기간 TODO.  
주요 무결성 규칙: 실제 개인정보는 개발·테스트에 사용하지 않는다.

## EMPLOYEE

목적: 직원과 소속 부서 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| employee_id | BIGINT | N | PK | IDENTITY | 직원 ID |
| employee_no | VARCHAR(30) | N | - | UNIQUE | 사번 |
| department_id | BIGINT | N | FK | DEPARTMENT | 현 소속 |
| active | BOOLEAN | N | - | DEFAULT TRUE | 활성 여부 |

인덱스: `(department_id, active)`.  
삭제 정책: 비활성화.  
주요 무결성 규칙: 부서 변경 이력 테이블 도입 여부 TODO.

## DEPARTMENT

목적: 조직과 데이터 접근 범위 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| department_id | BIGINT | N | PK | IDENTITY | 부서 ID |
| code | VARCHAR(30) | N | - | UNIQUE | 부서 코드 |
| name | VARCHAR(100) | N | - | - | 부서명 |

인덱스: `UK(code)`. 삭제 정책: 사용 중이면 삭제 금지. 주요 무결성 규칙: 조직 계층은 TODO.

## ROLE

목적: 업무 역할 정의.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| role_id | BIGINT | N | PK | IDENTITY | 역할 ID |
| code | VARCHAR(50) | N | - | UNIQUE | 불변 코드 |
| description | VARCHAR(200) | Y | - | - | 설명 |

인덱스: `UK(code)`.  
삭제 정책: 비활성화 권장.  
주요 무결성 규칙: 사용 중인 역할은 물리 삭제하지 않는다.

## PERMISSION

목적: 리소스별 세부 권한 정의.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| permission_id | BIGINT | N | PK | IDENTITY | 권한 ID |
| code | VARCHAR(50) | N | - | UNIQUE | 불변 권한 코드 |
| description | VARCHAR(200) | Y | - | - | 설명 |

인덱스: `UK(code)`.  
삭제 정책: 비활성화 권장.  
주요 무결성 규칙: 사용 중인 권한은 물리 삭제하지 않는다.

## EMPLOYEE_ROLE

목적: 직원에게 하나 이상의 업무 역할을 부여하는 MVP 연결 테이블.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| employee_id | BIGINT | N | PK, FK | EMPLOYEE | 직원 |
| role_id | BIGINT | N | PK, FK | ROLE | 부여 역할 |
| assigned_at | TIMESTAMPTZ | N | - | DEFAULT now() | 부여 시각 |

인덱스: `PK(employee_id, role_id)`, 역방향 조회용 `(role_id, employee_id)`.  
삭제 정책: 역할 회수 시 MVP에서는 연결 행 삭제. 권한 변경 감사로그는 별도 보존한다.  
주요 무결성 규칙: 동일 직원·역할 중복 부여 금지. `valid_from`/`valid_to`와 부여 이력 보존은 향후 확장 TODO.

## CUSTOMER_ROLE

목적: 고객에게 고객용 업무 역할을 부여하는 MVP 연결 테이블.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| customer_id | BIGINT | N | PK, FK | CUSTOMER | 고객 |
| role_id | BIGINT | N | PK, FK | ROLE | 부여 역할 |
| assigned_at | TIMESTAMPTZ | N | - | DEFAULT now() | 부여 시각 |

인덱스: `PK(customer_id, role_id)`, 역방향 조회용 `(role_id, customer_id)`.  
삭제 정책: 역할 회수 시 MVP에서는 연결 행 삭제. 권한 변경 감사로그는 별도 보존한다.  
주요 무결성 규칙: 고객에게 부여 가능한 역할 코드 범위는 애플리케이션에서 검증하고 DB 강제 방식은 TODO. 고객·직원 공통 `USER_ACCOUNT` 도입 여부가 결정되면 연결 주체를 재검토한다.

## ROLE_PERMISSION

목적: 역할이 허용하는 세부 권한을 정의하는 MVP 연결 테이블.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| role_id | BIGINT | N | PK, FK | ROLE | 역할 |
| permission_id | BIGINT | N | PK, FK | PERMISSION | 권한 |
| assigned_at | TIMESTAMPTZ | N | - | DEFAULT now() | 연결 시각 |

인덱스: `PK(role_id, permission_id)`, 역방향 조회용 `(permission_id, role_id)`.  
삭제 정책: 권한 회수 시 MVP에서는 연결 행 삭제. 관리자 설정 변경 감사로그는 별도 보존한다.  
주요 무결성 규칙: 동일 역할·권한 중복 연결 금지. `valid_from`/`valid_to`는 향후 확장 TODO.

## FINANCIAL_PRODUCT

목적: 정책금융 상품 기본정보 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| product_id | BIGINT | N | PK | IDENTITY | 상품 ID |
| code | VARCHAR(30) | N | - | UNIQUE | 상품 코드 |
| name | VARCHAR(100) | N | - | - | 상품명 |
| max_amount | NUMERIC(19,2) | N | - | CHECK > 0 | 최대 신청액 |
| active | BOOLEAN | N | - | DEFAULT TRUE | 활성 여부 |

인덱스: `(active, code)`. 삭제 정책: 비활성화. 주요 무결성 규칙: 금리·기간의 버전 귀속 여부 TODO.

## PRODUCT_RULE_VERSION

목적: 특정 시점에 적용되는 상품 규칙 스냅샷.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| rule_version_id | BIGINT | N | PK | IDENTITY | 버전 ID |
| product_id | BIGINT | N | FK | FINANCIAL_PRODUCT | 상품 |
| version_no | INTEGER | N | - | UNIQUE(product_id, version_no) | 버전 |
| valid_from | TIMESTAMPTZ | N | - | - | 적용 시작 시각 |
| valid_to | TIMESTAMPTZ | Y | - | CHECK(valid_to > valid_from) | 적용 종료 시각 |

인덱스: `(product_id, valid_from, valid_to)`. 삭제 정책: 참조 후 삭제 금지. 주요 무결성 규칙: 유효기간 중첩 방지는 exclusion constraint 검토.

## ELIGIBILITY_RULE

목적: 버전별 자격조건과 평가 순서 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| rule_id | BIGINT | N | PK | IDENTITY | 규칙 ID |
| rule_version_id | BIGINT | N | FK | PRODUCT_RULE_VERSION | 규칙 버전 |
| rule_type | VARCHAR(40) | N | - | CHECK/TODO | 연령·소득 등 |
| operator | VARCHAR(20) | N | - | CHECK/TODO | 비교 연산자 |
| comparison_value | VARCHAR(200) | N | - | TODO | 비교 기준값 |

인덱스: `(rule_version_id, rule_type)`. 삭제 정책: 버전 확정 후 불변. 주요 무결성 규칙: 안전한 규칙 표현식 형식 TODO.

## APPLICATION

목적: 고객 신청과 신청 당시 정책 참조 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| application_id | BIGINT | N | PK | IDENTITY | 신청 ID |
| customer_id | BIGINT | N | FK | CUSTOMER | 신청 고객 |
| product_id | BIGINT | N | FK | FINANCIAL_PRODUCT | 상품 |
| rule_version_id | BIGINT | N | FK | PRODUCT_RULE_VERSION | 접수 시 규칙 |
| status | VARCHAR(30) | N | - | CHECK | 현재 상태 |
| requested_amount | NUMERIC(19,2) | N | - | CHECK > 0 | 신청액 |
| received_at | TIMESTAMPTZ | Y | - | - | 접수 시각 |
| version | BIGINT | N | - | DEFAULT 0 | 낙관적 락 |

인덱스: `(customer_id, received_at DESC)`, `(status, received_at)`, 중복신청 부분 인덱스 TODO.  
삭제 정책: 접수 후 삭제 금지.  
주요 무결성 규칙: 신청 상품과 규칙 버전의 상품 일치 검증 필요.

## APPLICATION_ASSIGNMENT

목적: 신청의 담당 부서·담당 직원 배정과 재배정 이력을 보존하고 현재 담당자를 식별한다.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| assignment_id | BIGINT | N | PK | IDENTITY | 배정 이력 ID |
| application_id | BIGINT | N | FK | APPLICATION | 대상 신청 |
| department_id | BIGINT | N | FK | DEPARTMENT | 담당 부서 |
| employee_id | BIGINT | N | FK | EMPLOYEE | 담당 직원 |
| assigned_at | TIMESTAMPTZ | N | - | DEFAULT now() | 배정 시각 |
| assigned_by_employee_id | BIGINT | N | FK | EMPLOYEE | 배정 처리자 |
| released_at | TIMESTAMPTZ | Y | - | CHECK(released_at >= assigned_at) | 해제·재배정 시각 |

인덱스: 현재 담당자 조회용 PostgreSQL 부분 UNIQUE 인덱스 `UNIQUE(application_id) WHERE released_at IS NULL`, 담당자 업무목록용 `(employee_id, released_at, assigned_at)`, 부서별 조회용 `(department_id, released_at)`.  
삭제 정책: 물리 삭제 금지. 잘못된 배정도 해제 처리하고 이력을 보존한다.  
주요 무결성 규칙: 신청당 활성 배정은 최대 하나이다. `employee_id`의 현 소속이 `department_id`와 같은지는 애플리케이션에서 검증하며 과거 부서 변경 후에도 배정 당시 부서 FK를 보존한다. MVP는 직원이 수행하는 수동 배정만 지원하며 자동 배정의 시스템 행위자 표현은 향후 TODO이다.

## APPLICATION_DOCUMENT

목적: 신청 증빙파일 메타데이터 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| document_id | BIGINT | N | PK | IDENTITY | 문서 ID |
| application_id | BIGINT | N | FK | APPLICATION | 신청 |
| document_type | VARCHAR(40) | N | - | - | 서류 종류 |
| storage_key | VARCHAR(300) | N | - | UNIQUE | 외부 저장소 키 |
| checksum | VARCHAR(128) | N | - | - | 무결성 해시 |

인덱스: `(application_id, document_type)`. 삭제 정책: 보관 규정에 따른 논리 삭제. 주요 무결성 규칙: 파일 본문 DB 저장 여부 TODO.

## SCREENING_RESULT

목적: 자동·수동 심사 결과와 근거 보존.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| screening_id | BIGINT | N | PK | IDENTITY | 심사 ID |
| application_id | BIGINT | N | FK | APPLICATION | 신청 |
| employee_id | BIGINT | Y | FK | EMPLOYEE | 시스템 심사는 NULL |
| result | VARCHAR(20) | N | - | CHECK | 결과 |
| reason | TEXT | N | - | - | 판단 근거 |

인덱스: `(application_id, screening_id)`. 삭제 정책: 삭제 금지. 주요 무결성 규칙: 결과 수정 대신 신규 버전 추가.

## STATUS_HISTORY

목적: 신청 상태 전이 이력.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| history_id | BIGINT | N | PK | IDENTITY | 이력 ID |
| application_id | BIGINT | N | FK | APPLICATION | 신청 |
| before_status | VARCHAR(30) | N | - | CHECK | 변경 전 상태 |
| after_status | VARCHAR(30) | N | - | CHECK | 변경 후 상태 |
| actor_id | VARCHAR(100) | N | - | - | 처리자 |
| reason | TEXT | N | - | - | 변경 사유 |
| changed_at | TIMESTAMPTZ | N | - | - | 변경 시각 |

인덱스: `(application_id, changed_at)`. 삭제 정책: 삭제 금지. 주요 무결성 규칙: 변경 전후 상태 동일 금지.

## LOAN

목적: 실행된 대출계약과 현재 잔액 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| loan_id | BIGINT | N | PK | IDENTITY | 대출 ID |
| application_id | BIGINT | N | FK | UNIQUE | 신청당 하나 |
| principal | NUMERIC(19,2) | N | - | CHECK > 0 | 실행 원금 |
| outstanding_balance | NUMERIC(19,2) | N | - | CHECK >= 0 | 현재 잔액 |
| executed_at | TIMESTAMPTZ | N | - | - | 실행 시각 |
| version | BIGINT | N | - | DEFAULT 0 | 동시성 제어 |

인덱스: `UK(application_id)`. 삭제 정책: 삭제 금지. 주요 무결성 규칙: 잔액은 원장과 정기 대사.

## REPAYMENT_SCHEDULE

목적: 회차별 납부 예정액과 상태 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| schedule_id | BIGINT | N | PK | IDENTITY | 일정 ID |
| loan_id | BIGINT | N | FK | LOAN | 대출 |
| installment_no | INTEGER | N | - | UNIQUE(loan_id, installment_no) | 회차 |
| due_date | DATE | N | - | - | 납부예정일 |
| principal | NUMERIC(19,2) | N | - | CHECK >= 0 | 예정 원금 |
| interest | NUMERIC(19,2) | N | - | CHECK >= 0 | 예정 이자 |
| status | VARCHAR(20) | N | - | CHECK | 납부 상태 |

인덱스: `(loan_id, installment_no)`, `(status, due_date)`. 삭제 정책: 삭제 금지. 주요 무결성 규칙: 합계와 계약금액 검증.

## FINANCIAL_TRANSACTION

목적: 실행·상환·정정 금융거래 원장.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| transaction_id | BIGINT | N | PK | IDENTITY | 거래 ID |
| loan_id | BIGINT | N | FK | LOAN | 대출 |
| transaction_key | UUID/VARCHAR | N | - | UNIQUE | 거래 고유키 |
| type | VARCHAR(30) | N | - | CHECK | 거래 종류 |
| amount | NUMERIC(19,2) | N | - | CHECK > 0 | 금액 |
| created_at | TIMESTAMPTZ | N | - | - | 거래 시각 |

인덱스: `(loan_id, created_at)`, `UK(transaction_key)`. 삭제 정책: append-only, 삭제 금지. 주요 무결성 규칙: 정정은 반대 거래로 처리.

## OVERDUE

목적: 연체 발생·해소 기간과 산정 결과 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| overdue_id | BIGINT | N | PK | IDENTITY | 연체 ID |
| loan_id | BIGINT | N | FK | LOAN | 대출 |
| schedule_id | BIGINT | N | FK | REPAYMENT_SCHEDULE | 미납 회차 |
| status | VARCHAR(20) | N | - | CHECK | 연체 상태 |
| started_at | TIMESTAMPTZ | N | - | - | 연체 시작 시각 |
| resolved_at | TIMESTAMPTZ | Y | - | CHECK(resolved_at >= started_at) | 해소 시각 |

인덱스: `(status, started_at)`, `(loan_id, status)`. 삭제 정책: 삭제 금지. 주요 무결성 규칙: 동일 일정의 활성 연체 하나.

## AUDIT_LOG

목적: 개인정보와 중요 업무 행위의 감사 추적.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| audit_id | BIGINT | N | PK | IDENTITY | 감사 ID |
| actor_id | VARCHAR(100) | N | - | - | 인증 주체 ID |
| employee_id | BIGINT | Y | FK | EMPLOYEE | 직원인 경우의 ID |
| action | VARCHAR(50) | N | - | - | 행위 코드 |
| target_type | VARCHAR(50) | N | - | - | 대상 리소스 종류 |
| target_id | VARCHAR(100) | N | - | - | 대상 식별자 |
| success | BOOLEAN | N | - | - | 성공 여부 |
| created_at | TIMESTAMPTZ | N | - | - | 처리 시각 |
| detail | JSONB | Y | - | - | 비민감 상세 |

인덱스: `(employee_id, created_at)`, `(target_type, target_id, created_at)`. 삭제 정책: 보존기간 내 삭제 금지. 주요 무결성 규칙: 민감 원문·비밀번호·토큰 저장 금지.

## RISK_ALERT

목적: 설명 가능한 이상행위 탐지 결과 관리.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| alert_id | BIGINT | N | PK | IDENTITY | 경보 ID |
| rule_code | VARCHAR(50) | N | - | - | 탐지 규칙 |
| customer_id | BIGINT | Y | FK | CUSTOMER | 관련 고객 |
| application_id | BIGINT | Y | FK | APPLICATION | 관련 신청 |
| loan_id | BIGINT | Y | FK | LOAN | 관련 대출 |
| transaction_id | BIGINT | Y | FK | FINANCIAL_TRANSACTION | 관련 금융거래 |
| evidence | JSONB | N | - | - | 판단 근거 |
| status | VARCHAR(20) | N | - | CHECK | 처리 상태 |

인덱스: `(status, alert_id)`, 각 nullable FK의 값이 존재하는 행만 대상으로 하는 부분 인덱스 `(customer_id)`, `(application_id)`, `(loan_id)`, `(transaction_id)`.  
삭제 정책: 보존기간 내 삭제 금지.  
주요 무결성 규칙: 네 대상 FK 중 적어도 하나가 존재하도록 `CHECK (num_nonnulls(customer_id, application_id, loan_id, transaction_id) >= 1)` 적용을 제안한다. 정확히 하나만 허용할지는 TODO이며 여러 FK가 존재할 경우 서로 같은 업무 흐름에 속하는지 애플리케이션에서 검증한다.

## IDEMPOTENCY_REQUEST

목적: 재전송 요청의 단일 처리와 결과 재사용.

| 컬럼 | 타입 | NULL | PK/FK | 제약조건 | 설명 |
|---|---|---|---|---|---|
| idempotency_id | BIGINT | N | PK | IDENTITY | ID |
| scope | VARCHAR(50) | N | - | UNIQUE(scope, requester_id, idempotency_key) | 업무 범위 |
| requester_id | VARCHAR(100) | N | - | UNIQUE(scope, requester_id, idempotency_key) | 요청자 |
| idempotency_key | VARCHAR(100) | N | - | UNIQUE(scope, requester_id, idempotency_key) | 멱등성 키 |
| request_hash | VARCHAR(128) | N | - | - | 본문 동일성 확인 |
| status | VARCHAR(20) | N | - | CHECK | 처리 상태 |
| response_ref | VARCHAR(200) | Y | - | - | 최초 처리 결과 참조 |
| expires_at | TIMESTAMPTZ | N | - | - | 보존 만료 |

인덱스: 복합 UNIQUE, `(expires_at)`. 삭제 정책: 만료 후 정책에 따라 삭제. 주요 무결성 규칙: 처리 중 만료·재시도 정책 TODO.
