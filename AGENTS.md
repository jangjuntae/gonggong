# AGENTS.md

## 프로젝트 목표

공공 정책금융 신청·심사·사후관리 시스템은 단순 CRUD가 아니다. 금융거래 정확성, 공공 업무 규칙의 시스템화, 트랜잭션 처리, 데이터 무결성, 개인정보 접근통제, 감사로그, 멱등성, 동시성 제어, 장애 대응과 측정 기반 성능 개선 역량을 보여주는 것을 목표로 한다.

기술 기준은 Java, Spring Boot, Spring Security, Spring Data JPA와 PostgreSQL이며 초기 구조는 단일 애플리케이션이다.

## Codex 기본 행동 원칙

### 1. 문서를 먼저 확인한다

코드를 작성하거나 수정하기 전에 작업과 관련된 다음 문서를 확인한다.

```text
docs/specifications/00-project-overview.md
docs/specifications/01-requirements.md
docs/specifications/02-business-rules.md
docs/specifications/03-application-state-flow.md
docs/specifications/04-user-role-permission.md
docs/specifications/05-erd.md
docs/specifications/06-table-specification.md
docs/specifications/07-api-specification.md
docs/specifications/08-transaction-policy.md
docs/specifications/09-security-policy.md
docs/specifications/10-audit-log-policy.md
docs/specifications/11-test-strategy.md
docs/specifications/12-performance-test-plan.md
docs/specifications/13-failure-scenarios.md
docs/specifications/14-development-roadmap.md
docs/specifications/15-troubleshooting-log.md
```

문서와 코드가 충돌하면 임의로 한쪽을 변경하지 않는다. 충돌 위치, 실제 동작과 영향 범위를 사용자에게 알리고 기준을 확인한다.

### 2. 구현 상태를 추측하지 않는다

현재 저장소의 코드, DB 마이그레이션과 테스트를 직접 확인한다. 문서에만 있는 기능은 버그가 아닌 `미구현`으로 구분한다. 실행·측정하지 않은 결과를 성공으로 보고하지 않는다.

### 3. 금융 금액 계산

금액 계산에 `double`이나 `float`를 사용하지 않고 Java `BigDecimal`과 PostgreSQL `NUMERIC`을 사용한다. 통화 단위, scale, rounding mode와 계산 순서를 명시한다. 반올림 정책을 임의로 선택하지 않는다.

### 4. 상태 변경

신청 상태는 `docs/specifications/03-application-state-flow.md`에 정의된 전이만 허용한다. `DRAFT → APPROVED`, `REJECTED → EXECUTED`, `RECEIVED → REPAYING` 같은 전이는 차단한다. 상태 변경 코드에서는 처리자·시각·전후 상태·사유의 이력 기록도 확인한다.

### 5. 데이터 무결성

중요한 무결성을 애플리케이션 검증에만 의존하지 않는다. `NOT NULL`, `UNIQUE`, `FOREIGN KEY`, `CHECK` 적용 가능성을 함께 검토한다. 특히 중복 신청·대출 실행·상환·멱등성 키, 신청과 대출 관계 및 상태와 데이터의 불일치를 우선 확인한다.

### 6. 트랜잭션

신청 접수, 심사 결정, 대출 실행, 상환, 연체 상태 변경과 감사로그 기록의 업무 원자성을 확인한다. `@Transactional` 존재만으로 안전하다고 판단하지 않고 실제 호출 경계, 예외와 롤백, 외부 연동 및 부분 실패 결과를 검토한다.

### 7. 동시성

동일 신청의 동시 승인, 동일 대출의 동시 상환, 같은 멱등성 키의 동시 요청, Lost Update와 중복 대출 실행을 고려한다. `UNIQUE` 제약, 낙관적·비관적 락, atomic update와 Idempotency Key를 비교하고 현재 위험에 필요한 수준만 선택한다.

### 8. 보안

Authentication과 Authorization을 분리해 검토한다. 직원 접근은 역할뿐 아니라 담당 부서, 담당 업무와 리소스 범위를 확인한다. 개인정보·금융정보가 API 응답, 일반 로그, 예외, 감사로그와 테스트 데이터에 노출되지 않게 한다.

### 9. 감사로그

개인정보 조회·변경, 신청 상태 변경, 심사 결과, 승인·거절, 대출 실행, 금융거래와 관리자 설정 변경은 추적 가능해야 한다. 감사로그에는 필요한 행위 정보만 기록하고 민감정보 원문은 저장하지 않는다.

### 10. 테스트

정상 경로뿐 아니라 잘못된 상태 전이, 중복·동시 요청, 권한 없는 접근, 부분 실패, 롤백과 금융 계산 경계값을 우선 검증한다. 중요한 업무 규칙마다 하나 이상의 실패 테스트를 둔다.

### 11. 성능

초기 단계의 추측성 최적화를 피하되 N+1, 무제한 전체 조회, 페이지네이션 누락, 불필요한 eager loading, 명백한 full scan 위험과 인덱스 누락을 알린다. 개선 완료 여부는 가능한 경우 `EXPLAIN ANALYZE`와 재현 가능한 측정으로 판단한다.

## 작업 종료 전 Self Review

코드 생성 또는 수정 후 다음을 확인한다.

```text
[ ] 요구사항과 맞는가
[ ] 업무 규칙을 위반하지 않는가
[ ] 잘못된 상태 전이가 가능한가
[ ] 트랜잭션 경계가 적절한가
[ ] 부분 실패 시 데이터가 깨질 수 있는가
[ ] 중복 요청이 문제를 일으킬 수 있는가
[ ] 동시 요청에 취약한가
[ ] DB 제약조건이 필요한가
[ ] 금액에 double/float가 사용되지 않았는가
[ ] 개인정보가 노출되지 않는가
[ ] 감사로그가 필요한 작업인가
[ ] 실패 테스트가 필요한가
```

## 리뷰 에이전트 문서

- `docs/agent-prompts/review-agent.md`: 전체 품질 리뷰
- `docs/agent-prompts/transaction-db-agent.md`: DB·트랜잭션·동시성 심층 리뷰
- `docs/agent-prompts/security-audit-agent.md`: 접근통제·개인정보·감사 심층 리뷰
- `docs/agent-prompts/feature-completion-agent.md`: 단일 기능 완료 여부 판정
