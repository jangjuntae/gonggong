# Project Learning Log

## 작성 목적

이 프로젝트에서 사용한 기술을 단순히 적용하는 데 그치지 않고,
각 기술이 왜 필요한지, 어떤 문제를 해결하는지,
다른 선택지는 무엇인지 이해하기 위한 기록이다.

이 문서는 작업 목록을 나열하는 CHANGELOG가 아니다. 구현 과정에서 내린 선택의 이유와 기술의 역할을 내 언어로 설명하고, 이후 면접에서 "왜 이렇게 구현했는가?"라는 질문에 답할 수 있도록 단계별로 갱신한다.

---

## STEP 01. Spring Boot + PostgreSQL 환경 구성

### 이번 단계에서 한 것

- Java 17과 Gradle 기반 Spring Boot 프로젝트의 기본 실행 환경을 구성했다.
- Web, JPA, Validation, Security, PostgreSQL, Flyway, Actuator, OpenAPI 의존성을 추가했다.
- `local`과 `test` Profile을 분리하고, 로컬 DB 접속 정보를 환경변수로 주입하도록 구성했다.
- Docker Compose로 로컬 개발용 PostgreSQL을 실행할 수 있게 했다.
- Flyway가 관리하는 최소 초기 스키마를 만들고 Hibernate는 해당 스키마를 검증하도록 했다.
- Testcontainers 기반 통합 테스트에서 Spring Context, PostgreSQL 연결, Flyway 적용, JPA 초기화를 함께 확인하도록 구성했다.
- 최소 공통 오류 응답과 개발 기반 단계의 보안 설정을 추가했다.

### 전체 실행 흐름

```text
Spring Boot 실행
→ application.yml 로딩
→ 활성 Profile에 맞는 추가 설정 로딩
→ 환경변수로 DataSource 접속 정보 구성
→ PostgreSQL 연결
→ Flyway Migration 실행
→ Hibernate가 DB 스키마 검증
→ EntityManager 초기화
→ Application 실행
```

테스트에서는 로컬 Docker Compose DB 대신 Testcontainers가 별도의 PostgreSQL 컨테이너를 만들고, `@ServiceConnection`이 그 접속 정보를 Spring Boot에 전달한다.

### 이해해야 할 핵심 개념

#### Spring Profile

한 문장 설명:

실행 환경별로 서로 다른 Spring 설정을 선택해서 적용하는 기능이다.

현재 프로젝트에서 사용하는 이유:

로컬 개발용 PostgreSQL 설정과 격리된 통합 테스트 설정을 분리해, 한 환경의 접속 정보나 실행 방식이 다른 환경에 영향을 주지 않게 하기 위해 사용한다.

실제 적용 위치:

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/test/resources/application-test.yml`
- `PolicyFinanceApplicationTests`의 `@ActiveProfiles("test")`

내가 이해한 내용:

공통 설정은 `application.yml`에 두고 환경마다 달라지는 값만 Profile 파일에 둔다. 현재 기본 Profile은 `local`이며, 테스트는 `test`를 명시적으로 활성화한다. Profile은 애플리케이션 코드를 바꾸지 않고 실행 환경을 전환하기 위한 설정 경계다.

#### DataSource

한 문장 설명:

애플리케이션이 데이터베이스 연결을 얻고 관리할 수 있게 해주는 JDBC 연결 정보와 커넥션 풀의 추상화다.

현재 프로젝트에서 사용하는 이유:

Spring Data JPA, Flyway와 Hibernate가 같은 PostgreSQL에 연결하여 마이그레이션과 데이터 접근을 수행할 수 있어야 하기 때문이다.

실제 적용 위치:

- `src/main/resources/application-local.yml`의 `spring.datasource`
- `PolicyFinanceApplicationTests`의 `@ServiceConnection`

내가 이해한 내용:

로컬 실행에서는 URL, 사용자명, 비밀번호를 환경변수로 받아 Spring Boot가 DataSource를 자동 구성한다. 테스트에서는 Testcontainers의 접속 정보가 `@ServiceConnection`을 통해 자동 구성된다. JPA가 PostgreSQL에 직접 연결되는 것이 아니라 DataSource에서 확보한 연결을 사용한다.

#### Docker Compose

한 문장 설명:

여러 컨테이너의 이미지, 환경변수, 포트, 볼륨과 상태 확인 방법을 파일로 정의하고 동일한 명령으로 실행하는 도구다.

현재 프로젝트에서 사용하는 이유:

개발자가 PostgreSQL을 직접 설치하고 설정하지 않아도 프로젝트에 맞는 DB 버전과 실행 조건을 재현할 수 있게 하기 위해 사용한다.

실제 적용 위치:

- `compose.yml`
- `.env.example`

내가 이해한 내용:

Compose는 로컬에서 애플리케이션을 실행하고 데이터를 유지하며 개발할 때 사용하는 PostgreSQL을 제공한다. named volume은 컨테이너가 재생성되어도 데이터를 유지하고, healthcheck는 프로세스가 떠 있는지만이 아니라 DB가 연결을 받을 준비가 되었는지 판단한다. 비밀번호는 Compose 파일에 고정하지 않고 `.env` 또는 환경변수로 전달한다.

#### Flyway

한 문장 설명:

버전이 지정된 SQL 마이그레이션을 순서대로 적용하고 적용 이력을 데이터베이스에 기록하는 스키마 변경 관리 도구다.

현재 프로젝트에서 사용하는 이유:

DB 스키마 변경을 코드처럼 버전 관리하고, 개발·테스트 환경에 같은 변경 순서를 재현하기 위해 사용한다.

실제 적용 위치:

- `build.gradle`의 Flyway 의존성
- `src/main/resources/db/migration/V1__init.sql`
- `src/main/resources/application.yml`의 `spring.flyway.enabled`

내가 이해한 내용:

Flyway는 애플리케이션 시작 과정에서 아직 적용되지 않은 마이그레이션을 버전 순서대로 실행하고 `flyway_schema_history`에 결과를 남긴다. 현재 V1은 도구의 동작을 검증하기 위한 최소 스키마만 만들며, 확정되지 않은 전체 도메인 테이블을 미리 생성하지 않는다.

#### JPA ddl-auto

한 문장 설명:

Hibernate가 엔티티 매핑을 기준으로 DB 스키마를 생성·변경·검증할 방법을 정하는 설정이다.

현재 프로젝트에서 사용하는 이유:

스키마 변경 책임은 Flyway에 두고, Hibernate에는 엔티티와 실제 스키마가 일치하는지만 확인하게 하기 위해 사용한다.

실제 적용 위치:

- `src/main/resources/application.yml`의 `spring.jpa.hibernate.ddl-auto: validate`

내가 이해한 내용:

`validate`는 Hibernate가 테이블을 임의로 만들거나 수정하지 않고 매핑과 스키마의 불일치를 시작 시점에 발견하게 한다. `update`는 편리하지만 자동 변경 내용이 명확한 마이그레이션 기록으로 남지 않아 환경별 스키마가 달라질 수 있으므로 이 프로젝트의 관리 방식과 맞지 않는다.

#### Testcontainers

한 문장 설명:

통합 테스트가 실행되는 동안 실제 서비스와 같은 종류의 의존 시스템을 컨테이너로 생성하고 종료하는 테스트 도구다.

현재 프로젝트에서 사용하는 이유:

PostgreSQL 고유 동작과 Flyway SQL을 인메모리 DB로 대신하지 않고 실제 PostgreSQL 환경에서 검증하기 위해 사용한다.

실제 적용 위치:

- `build.gradle`의 Testcontainers 의존성
- `src/test/java/com/gonggong/policyfinance/PolicyFinanceApplicationTests.java`

내가 이해한 내용:

테스트마다 격리된 PostgreSQL 환경을 준비하므로 개발자의 로컬 DB 상태에 영향을 덜 받는다. `@ServiceConnection`은 컨테이너의 동적 포트와 계정 정보를 Spring Boot 설정에 연결한다. 테스트는 컨텍스트 로딩뿐 아니라 V1 데이터와 Flyway 이력 테이블을 조회해 DB 연결, 마이그레이션, EntityManager 초기화를 함께 검증한다.

### 주요 설계 선택

| 선택 | 이유 | 다른 선택지 |
| --- | --- | --- |
| PostgreSQL | 실제 구현에서 사용할 DB와 개발·테스트 DB를 통일하고 FK, 제약조건, 트랜잭션 등 PostgreSQL 동작을 그대로 검증하기 위해 선택했다. | MySQL, MariaDB, H2 등 |
| Flyway | SQL 중심의 단순한 버전 마이그레이션이 현재 MVP 규모에 적합하고 변경 순서와 이력을 명확히 남길 수 있다. | Liquibase, 수동 SQL 관리 등 |
| `ddl-auto=validate` | Flyway만 스키마를 변경하게 하고 Hibernate는 엔티티 매핑의 불일치만 빠르게 발견하도록 책임을 분리한다. | `create`, `create-drop`, `update`, `none` |
| Testcontainers | H2와 PostgreSQL의 문법·제약조건·동작 차이로 생기는 거짓 성공을 줄이고 실제 DB 기반 통합 테스트를 수행한다. | H2, 공유 테스트 DB, 로컬 Compose DB 등 |

### 직접 확인한 파일

- `build.gradle`: Java 버전, Spring Boot 플러그인과 런타임·테스트 의존성
- `src/main/resources/application.yml`: 공통 Profile, JPA, Flyway 설정
- `src/main/resources/application-local.yml`: 환경변수 기반 PostgreSQL DataSource
- `src/test/resources/application-test.yml`: 테스트 Profile
- `compose.yml`: 로컬 PostgreSQL, 볼륨, 포트, healthcheck
- `.env.example`: 로컬 실행에 필요한 환경변수 예시
- `src/main/resources/db/migration/V1__init.sql`: 최초 Flyway 마이그레이션
- `src/test/java/com/gonggong/policyfinance/PolicyFinanceApplicationTests.java`: PostgreSQL·Flyway·JPA 통합 테스트

### 발생한 문제

문제:

Testcontainers 통합 테스트 실행 중 Docker Hub에서 `testcontainers/ryuk` 및 PostgreSQL 이미지를 가져오는 과정이 EOF 오류로 실패했다.

원인:

테스트 코드나 Spring 설정 오류가 아니라 현재 실행 환경의 Docker Hub 이미지 다운로드 연결 문제였다. 따라서 컨테이너가 생성되기 전 단계에서 테스트가 중단되었다.

해결:

프로젝트 구성과 테스트 소스 컴파일, Compose 설정 유효성은 별도로 확인했다. 이미지 다운로드가 가능한 네트워크 환경에서 `./gradlew test`를 다시 실행해 최종 통과 여부를 확인해야 한다.

배운 점:

테스트 코드가 컴파일되는 것과 통합 테스트가 실제로 성공하는 것은 다르다. 외부 인프라 문제로 실행하지 못한 결과를 성공으로 간주하면 안 되며, 코드 결함과 실행 환경의 실패를 분리해서 기록해야 한다.

### 내가 설명할 수 있어야 하는 것

- [ ] Spring Boot가 PostgreSQL에 어떻게 연결되는지 설명할 수 있다.
- [ ] `application.yml`과 Profile의 역할을 설명할 수 있다.
- [ ] Docker Compose를 사용하는 이유를 설명할 수 있다.
- [ ] Flyway를 사용하는 이유를 설명할 수 있다.
- [ ] `ddl-auto=validate`를 사용하는 이유를 설명할 수 있다.
- [ ] Testcontainers를 사용하는 이유를 설명할 수 있다.

체크박스는 문서를 읽었다는 의미가 아니라, 자료 없이 내 말로 설명할 수 있을 때 직접 체크한다.

### 이해도 질문

1. Flyway와 Hibernate `ddl-auto`의 역할은 어떻게 다른가?
2. `ddl-auto`를 `update`가 아니라 `validate`로 사용하는 이유는 무엇인가?
3. Testcontainers에서 PostgreSQL을 사용하는 이유는 무엇인가?
4. Docker Compose PostgreSQL과 Testcontainers PostgreSQL의 역할은 어떻게 다른가?
5. Spring Boot는 어떤 과정을 통해 PostgreSQL에 연결되는가?

### 내 답변

#### Q1

Flyway는 버전이 지정된 SQL을 실행하여 DB 스키마를 의도적으로 변경하고 그 이력을 관리한다. Hibernate `ddl-auto`는 엔티티 매핑을 기준으로 스키마를 어떻게 다룰지 결정한다. 이 프로젝트에서는 변경은 Flyway가 담당하고 Hibernate는 `validate`로 일치 여부만 확인한다.

#### Q2

`update`는 Hibernate가 실행 시점에 스키마를 자동 변경하므로 어떤 SQL이 언제 적용되었는지 명확하게 관리하기 어렵고 환경별 차이가 생길 수 있다. `validate`를 사용하면 Flyway가 스키마 변경의 유일한 경로가 되고, 엔티티와 DB가 다르면 애플리케이션 시작 단계에서 오류를 발견할 수 있다.

#### Q3

실제 운영 기술로 선택한 PostgreSQL과 같은 DB 엔진에서 SQL, 제약조건, 타입과 마이그레이션을 검증하기 위해서다. H2를 사용하면 PostgreSQL에서는 실패할 코드가 테스트에서는 성공할 수 있다.

#### Q4

Docker Compose PostgreSQL은 개발자가 애플리케이션을 로컬에서 실행하고 개발 데이터를 유지할 때 사용하는 장기 실행 환경이다. Testcontainers PostgreSQL은 테스트 실행 때 자동으로 생성되고 종료되는 격리 환경이며, 반복 가능한 통합 테스트를 위한 것이다.

#### Q5

Spring Boot가 공통 설정과 활성 Profile 설정을 읽고 환경변수 값을 해석한 뒤 DataSource를 자동 구성한다. Flyway가 그 DataSource로 미적용 마이그레이션을 실행하고, 이후 Hibernate가 같은 DB 스키마와 엔티티 매핑을 검증하여 EntityManager를 초기화한다.

---

## 이후 학습 단계

아래 단계는 아직 구현하지 않았다. 각 단계가 완료되면 STEP 01과 같은 형식으로 실제 구현 내용, 선택 이유, 발생한 문제, 이해도 질문과 내 답변을 추가한다.

### STEP 02. 신청 상태 전이

학습 예정 개념: Enum, 상태 머신, 잘못된 상태 전이, 단위 테스트

### STEP 03. 상품과 규칙 버전

학습 예정 개념: Entity 관계, JPA 연관관계, FK, 정책 버전 관리

### STEP 04. 신청과 자격검증

학습 예정 개념: Validation, 업무 규칙, 신청 당시 데이터 보존

### STEP 05. 담당자 배정과 심사

학습 예정 개념: 권한, 상태 변경, 이력 관리

### STEP 06. 대출 실행

학습 예정 개념: `@Transactional`, 트랜잭션 원자성, Rollback

### STEP 07. 상환계획

학습 예정 개념: BigDecimal, 금융 계산, 반올림

### STEP 08. 상환

학습 예정 개념: 금융거래 원장, 잔액 변경, 트랜잭션

### STEP 09. 멱등성과 동시성

학습 예정 개념: Idempotency Key, UNIQUE Constraint, Optimistic Lock, Lost Update

### STEP 10. 권한과 감사로그

학습 예정 개념: Authentication, Authorization, RBAC, 개인정보 접근통제, Audit Log

### STEP 11. 성능

학습 예정 개념: Index, Composite Index, EXPLAIN ANALYZE, N+1, Pagination
