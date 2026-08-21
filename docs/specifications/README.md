# 프로젝트 문서

공공 정책금융 신청·심사·사후관리 시스템의 개발 전 설계와 개발 중 검증 결과를 관리한다.

| 문서 | 역할 |
|---|---|
| [00-project-overview.md](00-project-overview.md) | 프로젝트 목적, 사용자와 전체 업무 흐름 |
| [01-requirements.md](01-requirements.md) | 기능·비기능 요구사항과 식별자 |
| [02-business-rules.md](02-business-rules.md) | 핵심 업무 규칙과 검증·제약 방향 |
| [03-application-state-flow.md](03-application-state-flow.md) | 신청 상태 정의와 허용 전이 |
| [04-user-role-permission.md](04-user-role-permission.md) | 역할·부서 기반 권한 매트릭스 |
| [05-erd.md](05-erd.md) | 핵심 엔티티 관계 초안 |
| [06-table-specification.md](06-table-specification.md) | PostgreSQL 테이블·인덱스·삭제 정책 초안 |
| [07-api-specification.md](07-api-specification.md) | 도메인별 REST API 계약 초안 |
| [08-transaction-policy.md](08-transaction-policy.md) | 트랜잭션 경계, 원자성과 동시성 정책 |
| [09-security-policy.md](09-security-policy.md) | 인증·인가와 개인정보 보호 정책 |
| [10-audit-log-policy.md](10-audit-log-policy.md) | 감사 대상, 필드, 보호와 조회 정책 |
| [11-test-strategy.md](11-test-strategy.md) | 테스트 계층과 핵심 검증 시나리오 |
| [12-performance-test-plan.md](12-performance-test-plan.md) | 대량 데이터와 실행계획 기반 성능 계획 |
| [13-failure-scenarios.md](13-failure-scenarios.md) | 의도적으로 재현할 장애·문제 시나리오 |
| [14-development-roadmap.md](14-development-roadmap.md) | MVP·2차 개발 순서와 완료 기준 |
| [15-troubleshooting-log.md](15-troubleshooting-log.md) | 문제 재현부터 회고까지 누적 기록 |

> 이 프로젝트에서는 기술을 많이 사용하는 것보다 문제를 재현하고 원인을 분석한 뒤 근거를 가지고 해결하는 과정을 중요하게 다룬다.

## 갱신 원칙

- 결정되지 않은 항목은 `TODO`로 유지하고 결정 근거를 함께 기록한다.
- 구현하지 않은 기능, 측정하지 않은 성능과 검증하지 않은 결과를 완료로 표현하지 않는다.
- 요구사항 → 규칙 → 설계 → 테스트 → 트러블슈팅 간 식별자를 연결한다.
- 중요한 설계 변경은 영향받는 문서를 같은 변경에서 함께 갱신한다.
