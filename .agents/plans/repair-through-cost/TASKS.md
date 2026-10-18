# 할 일과 완료 증거

계획 원본: [README.md](README.md). 체크는 코드가 존재한다는 이유만으로 하지 않고, 옆에 실행 결과나 확인 근거를 기록한다. 실행할 수 없는 항목은 미체크로 두고 원인을 남긴다.

## A. 기준 확보와 컴파일

- [x] AGENTS.md와 관련 .agents 문서, 현재 diff를 읽고 사용자 변경을 확인한다.
- [x] ResourceRequest 생성에 dto.estimatedCost를 전달한다.
- [x] 필요한 import·시그니처를 정리하고 compileKotlin을 통과한다.
- [x] 기존 테스트 실패가 있다면 컴파일 오류와 DB·환경 실패를 구분해 기록한다.

증거: 사전 `./gradlew test --console=plain`은 `ResourceRequestService.kt:33:13`의 `estimatedCost` 전달 누락으로 `:compileKotlin FAILED`. `ResourceRequestService.createRequest`에 `estimatedCost = dto.estimatedCost`를 연결하고 미사용 import(`AuthDtos.kt`의 `Role`, `JwtTokenProvider.kt`의 `jakarta.persistence.Timeout.seconds`)를 정리한 뒤 `./gradlew compileKotlin --console=plain` 통과 확인. 이후 테스트 격리 전 `./gradlew test --console=plain` 실행 시 컴파일은 통과했으나 로컬 3306 포트의 타 프로젝트 컨테이너(`slotkey-mysql`)가 프로젝트 개발용 계정 접속을 거부(`ERROR 1045 (28000): Access denied`)하여 `contextLoads()`가 DB 연결 환경 문제로 실패함을 분리 확인함.

## B. 인증·인가와 경로

- [x] 서비스의 AccessDeniedException을 Spring Security 타입으로 바꾼다.
- [x] 컨트롤러의 requestSecurity 표현식과 서비스로 이동한 중복 인가를 정리한다.
- [x] ResourceRequestSecurityService의 남은 사용처를 확인하고 필요 없는 빈을 정리한다.
- [x] 생성·조회·취소·승인·반려 URL이 각각 연결되고 충돌 없이 컨텍스트가 뜬다.
- [x] 일반 가입 결과는 DEVELOPER이며 요청으로 관리자 역할을 얻을 수 없다.
- [x] 정상 JWT는 인증되고, 누락·만료·변조 JWT의 보호 API 요청은 401이다.
- [x] 개발자의 승인·반려, 감사자의 쓰기, 타인 신청 취소, 타인 userId 위장은 403이다.
- [x] 주입받은 서비스 프록시 직접 호출에서도 역할·ID 검사가 작동한다.
- [x] 권한 거부 후 DB 상태·내용이 변경되지 않는다.

증거: `ResourceRequestService.kt`의 `java.nio.file.AccessDeniedException`을 `org.springframework.security.access.AccessDeniedException`으로 교체. `ResourceRequestController.kt`에서 잘못된 빈 이름을 참조하던 `@PreAuthorize("@requestSecurity.isOwner(#id, principal)")` 및 서비스와 중복되던 `@PreAuthorize`를 제거하고, 참조가 0건이 된 `ResourceRequestSecurityService.kt`를 삭제함. `ApiSecurityAndValidationTest`에서 일반 가입의 `DEVELOPER` 고정(요청 본문에 `"role":"DEVOPS_ADMIN"` 주입 시에도 권한 상승 불가), 정상/누락/만료/변조 JWT의 200·401 응답, 역할 부족 및 타인 신청 취소 시 JSON 403(`FORBIDDEN`) 반환과 DB 무변경을 검증함. `ResourceRequestIntegrationTest`에서 실제 Spring 서비스 프록시 직접 호출 시 타인 `userId` 위장, 역할 부족, 타인 신청 취소가 모두 `org.springframework.security.access.AccessDeniedException`으로 거부되고 DB 상태/건수가 유지됨을 검증함.

## C. 상태 전이와 동시성

- [x] approve/reject/cancel이 모두 nextState 경로를 사용한다.
- [x] 중복 ensurePending과 불필요한 직접 상태 변경을 정리한다.
- [x] 반려 결과에 처리자와 사유가 함께 반영된다.
- [x] PENDING에서 세 명령의 정상 동작을 테스트한다.
- [x] APPROVED/REJECTED/CANCELLED/EXPIRED에서 추가 결정 거부를 테스트한다.
- [x] 실패한 전이는 기존 상태·처리자·사유를 변경하지 않는다.
- [x] @Version의 저장·재조회·증가를 확인한다.
- [x] 독립 트랜잭션 두 개의 동시 결정에서 하나만 커밋됨을 확인한다.
- [x] 잠금 예외가 실제 요청 경계에서 409로 변환되는지 확인한다.

증거: `ResourceRequest.kt`의 `approve`, `reject`, `cancel` 모두 `nextState(toState(), ...)`를 거치도록 통일하고 중복 `ensurePending()`을 제거함. `RequestStateTransitionTest`(8개 테스트)에서 순수 함수 `nextState`와 엔티티 메서드의 `PENDING` → `APPROVED`/`REJECTED`/`CANCELLED`/`EXPIRED` 전이 및 모든 완료 상태(`EXPIRED` 상태의 엔티티 `toState()` 분기 포함)에서의 재전이 거부(`RequestStateConflictException`), 실패 시 기존 상태·처리자·반려 사유·버전 보존을 검증함. `ResourceRequestIntegrationTest`에서 초기 저장 시 `version = 0`, 상태 변경 후 flush·영속성 컨텍스트 초기화·재조회 시 `version = 1` 증가를 확인하고, DB에 `EXPIRED` 상태로 저장된 엔티티를 flush·clear·재조회한 뒤 승인·반려·취소 요청이 모두 `RequestStateConflictException`으로 거부되고 상태·처리자·반려 사유·버전이 유지됨을 검증함. 또한 `CountDownLatch`와 `TransactionTemplate`을 사용한 두 독립 트랜잭션의 동시 승인·반려 경쟁에서 정확히 1건만 커밋되고 다른 1건은 `ObjectOptimisticLockingFailureException`으로 실패함을 검증함. `ApiSecurityAndValidationTest`에서 두 동시 HTTP 요청(`POST /api/requests/{id}/approve`와 `POST /api/requests/{id}/reject`)이 동일 `version = 0`을 읽고 경합할 때 실제 `MockMvc` 요청 경계에서 하나는 `200 OK`, 다른 하나는 `409 Conflict`(`CONCURRENT_MODIFICATION`) JSON으로 변환됨을 검증함.

## D. 비용·문자열·오류 응답

- [x] 비용이 요청 DTO → 엔티티 → DB → 응답 DTO까지 유지된다.
- [x] 비용 누락/null/음수/정밀도 초과 입력이 400이다.
- [x] spec 255자 및 목적 1023자의 DTO·컬럼 계약을 맞춘다.
- [x] 반려 사유 길이의 DTO·도메인·DB 제약을 맞춘다.
- [x] 유효 길이의 실제 저장과 한도 초과 입력 거부를 확인한다.
- [x] 필수값 누락/null/공백/잘못된 enum의 400 응답을 확인한다.
- [x] IllegalStateException 전체를 409로 처리하는 핸들러를 정리한다.
- [x] 업무 상태 충돌/잠금 충돌/권한 실패/입력 실패가 구분된다.
- [x] 오류 응답에 민감한 요청 원문이 포함되지 않는다.

증거: `CreateResourceRequestDto`(`@field:NotNull`, `@field:DecimalMin("0.00")`, `@field:Digits(integer = 12, fraction = 2)`), `ResourceRequest`(`precision = 14, scale = 2`, `spec` `length = 255`, `reason` `length = 1023`, `rejectReason` `length = 255`), `RejectRequestDto`(`@field:NotBlank`, `@field:Size(max = 255)`), `nextState` 도메인 검증(`reason.isNotBlank() && reason.length <= 255`)을 일치시킴. `GlobalExceptionHandler.kt`에서 광범위한 `IllegalStateException` → 409 핸들러를 제거하고 `MethodArgumentTypeMismatchException` 명시 핸들러를 추가하여 업무 상태 충돌(`INVALID_TRANSITION` 409), 낙관적 잠금 충돌(`CONCURRENT_MODIFICATION` 409), 권한 거부(`FORBIDDEN` 403), JSON 본문 및 쿼리·경로 바인딩 실패(`INVALID_REQUEST` 400, 고정 메시지 `"요청 형식과 필수 입력값을 확인해주세요."`)를 일관되게 분리함. `ResourceRequestIntegrationTest`와 `ApiSecurityAndValidationTest`에서 `spec` 255자, `reason` 1023자, `rejectReason` 255자, `estimatedCost` `999999999999.99`/`123456789012.34`의 실제 DB 저장·재조회·응답 DTO 반환과 경계 초과 입력의 400 응답, 잘못된 JSON enum 및 쿼리 파라미터(`GET /api/requests?status=INVALID_ENUM_SensitiveToken999`)·경로 변수(`POST /api/requests/not-a-number-SensitivePath999/approve`) 바인딩 실패 시 요청 원문·내부 클래스명·숫자 변환 예외 미노출을 검증함.

## E. 제출

- [x] 테스트용 DB 설정을 격리하고 실제 사용한 DB 종류를 기록한다.
- [x] 기존 version·비용·길이 컬럼 변경과 과거 비용 백필의 주의점을 기록한다.
- [x] 추가한 테스트를 실행하고 ./gradlew test 결과를 기록한다.
- [x] git diff --check를 통과한다.
- [x] 이번 범위에 대기 신청 수정 API·이력·카탈로그·만료 스케줄러가 추가되지 않았는지 확인한다.
- [x] 완료 항목의 근거, 미완료 항목의 원인, 변경 파일과 테스트 결과를 한국어로 보고한다.

증거:
- **테스트 DB 격리**: `backend/build.gradle.kts`에 `testRuntimeOnly("com.h2database:h2")`를 추가하고 `backend/src/test/resources/application.yaml`을 생성해 인메모리 H2(`jdbc:h2:mem:cloudportal_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`, `ddl-auto: create-drop`)로 기존 외부 DB를 건드리지 않고 격리 실행함.
- **스키마 변경 및 과거 비용 데이터 백필 주의점**:
  1. 기존 MySQL 테이블(`resource_requests`)을 유지한 채 반영할 때는 `version BIGINT NOT NULL DEFAULT 0`, `spec VARCHAR(255) NOT NULL`, `reason VARCHAR(1023) NOT NULL`, `reject_reason VARCHAR(255) NULL` 변경이 필요함.
  2. `estimated_cost DECIMAL(14, 2) NOT NULL`의 경우 기존 행이 존재한다면 **알 수 없는 과거 예상 비용을 임의의 `0.00`으로 채우지 말아야 함**(무료 신청과 미기록 데이터를 구분할 수 없어 감사·비용 통계가 왜곡됨).
  3. 기존 데이터가 있는 환경에서는 (a) 먼저 `ADD COLUMN estimated_cost DECIMAL(14, 2) NULL`로 추가하고, (b) 카탈로그 단가표·신청 당시 스펙 기록·운영자 확인 절차 등 명시적 백필 정책에 따라 실제 예상 비용을 채운 뒤, (c) `SELECT COUNT(*) FROM resource_requests WHERE estimated_cost IS NULL`이 `0`임을 확인하고 `MODIFY COLUMN estimated_cost DECIMAL(14, 2) NOT NULL`을 적용해야 함.
- **테스트 및 diff 검증 결과**: `./backend/gradlew -p backend clean test --console=plain` 실행 결과 전체 4개 테스트 클래스 21개 테스트 모두 통과(`BUILD SUCCESSFUL`). `git diff --check` 및 `git diff --cached --check` 공백/포맷 오류 없음.

## F. 재리뷰 보완 항목 (P2/P3)

- [x] P2: `main`과 `test`의 `application.yaml`에서 `open-in-view: false`와 `show-sql` 설정을 `spring.jpa` 바로 아래로 이동하고, Hibernate 전용 속성(`format_sql`, `default_batch_fetch_size`)은 `spring.jpa.properties.hibernate`에 유지한다. OSIV가 실제로 비활성화됨을 검증한다.
- [x] P2: `GlobalExceptionHandler`에서 `MethodArgumentTypeMismatchException`을 명시적으로 처리해 쿼리·경로 바인딩 오류도 고정 메시지의 공통 `INVALID_REQUEST` JSON 400을 반환하게 하고, 실제 보안 필터 체인을 통과하는 회귀 테스트로 입력 원문·내부 클래스명·숫자 변환 예외 미노출을 확인한다.
- [x] P3: 운영용 만료 API/스케줄러나 공개 상태 setter를 추가하지 않고 테스트 준비 단계에서만 `EXPIRED` 상태의 `ResourceRequest`를 구성·저장(flush/clear/재조회 포함)한 뒤 승인·반려·취소가 모두 `RequestStateConflictException`으로 거부되고 기존 상태·처리자·사유·버전이 유지되는 회귀 테스트를 추가한다.

증거:
- `backend/src/main/resources/application.yaml` 및 `backend/src/test/resources/application.yaml`에서 `open-in-view`와 `show-sql`을 `spring.jpa` 직하위로 이동함. `ResourceRequestIntegrationTest`에서 `environment.getProperty("spring.jpa.open-in-view", Boolean::class.java) == false`, `applicationContext.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java).isEmpty()`, 그리고 `src/main/resources/application.yaml`의 `spring.jpa.open-in-view == false` 및 `spring.jpa.show-sql == true`를 검증함. 테스트 실행 로그에서도 `spring.jpa.open-in-view is enabled by default` 경고가 사라짐을 확인함.
- `GlobalExceptionHandler.kt`에 `@ExceptionHandler(MethodArgumentTypeMismatchException::class)`를 추가하여 쿼리·경로 타입 불일치 시 `IllegalArgumentException` 핸들러(`BAD_REQUEST` + 예외 원문 노출)로 빠지지 않고 고정 메시지 `ErrorResponse("INVALID_REQUEST", "요청 형식과 필수 입력값을 확인해주세요.")` 400을 반환하도록 수정함. `ApiSecurityAndValidationTest`에서 관리자 JWT로 `GET /api/requests?status=INVALID_ENUM_SensitiveToken999` 및 `POST /api/requests/not-a-number-SensitivePath999/approve` 호출 시 `400 INVALID_REQUEST`가 반환되고 `No enum constant`, `RequestStatus`, `For input string`, 입력 토큰 원문이 응답 본문에 일절 포함되지 않음을 검증함.
- `RequestStateTransitionTest`(단위)와 `ResourceRequestIntegrationTest`(H2 영속화 + `saveAndFlush` + `entityManager.clear()` + 재조회)에서 `EXPIRED` 상태의 `ResourceRequest`가 `toState()`의 `RequestStatus.EXPIRED -> RequestState.Expired` 분기를 실제로 타며 승인·반려·취소 시 모두 `RequestStateConflictException`으로 거부되고 `status == EXPIRED`, `approver == null`, `rejectReason == null`, `version`이 그대로 유지됨을 검증함.
- 최종 테스트 수: 총 4개 테스트 클래스 **21개 테스트** (`CloudportalPractice261018ApplicationTests` 1개, `RequestStateTransitionTest` 8개, `ResourceRequestIntegrationTest` 5개, `ApiSecurityAndValidationTest` 7개) 전원 통과.

## 후속 단계로 남길 항목

대기 신청 수정 API, 승인·반려 이력과 원자적 저장, 카탈로그/스냅샷, 만료 실행 정책은 이번 완료 조건에 포함하지 않는다. 테스트 환경 제약으로 확인하지 못한 내용은 아래에 별도로 적는다.

미완료·환경 제약:
- 로컬 3306 포트에서 실행 중인 Docker 컨테이너(`slotkey-mysql`, MySQL 8.4)가 현재 프로젝트의 개발용 계정(프로젝트 개발용 계정)과 다른 자격증명을 사용하고 있어(`ERROR 1045 (28000): Access denied for user 'root'@'localhost'`) 실제 MySQL 인스턴스에 대한 직접 연결 및 `ddl-auto: update` 자동 ALTER 동작은 실행하지 못함. 기존 컨테이너/데이터를 임의로 초기화하지 않고 `src/test/resources/application.yaml`의 H2(`MODE=MySQL`) 격리 환경에서 전체 통합·동시성·웹 테스트(21개)를 검증 완료함. 실제 MySQL 환경에 배포·기동할 때는 위 E절에 기록한 수동 ALTER 및 비용 백필 절차를 선행해야 함.
