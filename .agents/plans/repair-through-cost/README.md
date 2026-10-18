# 비용·DTO 단계까지의 수정 보완 계획

## 목표와 범위

사용자가 직접 적용한 코드를 보존하면서, [수정 가이드](../../guides/review-fixes/README.md)의 01~03장과 04장의 **예상 비용과 DTO 검증**까지 연결·보완한다. 결과는 현재 워킹트리의 코드·테스트·문서로 제출한다.

대기 신청 수정 API, 승인·반려 이력, 카탈로그는 다음 학습 단계로 남긴다. `EXPIRED`의 타입과 완료 상태 규칙은 점검하되, 미결정인 만료 조건·스케줄러는 이번에 구현하지 않는다. 범위 해석은 사용자의 “대기 신청 수정 API 전까지”를 기준으로 했다.

작업 진행과 완료 증거는 [TASKS.md](TASKS.md), 실행 에이전트에 전달할 지시는 [HANDOFF.md](HANDOFF.md)에서 관리한다. 이 문서는 계획이며 완료 보고서가 아니다.

## 기준 시점과 현재 상태

2026-10-18, HEAD `b35c81b`의 미커밋 변경을 포함한 소스를 확인했다. 작업 시작 시 diff를 다시 읽고 이후 변경도 보존한다. 기존 IDE 설정과 문서 변경은 이번 로직 수정과 별도로 취급한다.

| 영역 | 확인된 구현 | 남은 문제·검증 |
| --- | --- | --- |
| 가입 | DTO role 제거, 서버에서 DEVELOPER 지정, non-null User 생성 | 실제 HTTP 가입과 역할 저장 검증 |
| JWT | 만료 설정 placeholder 수정 | 기동·정상/만료/변조 토큰 검증, 불필요한 import 정리 |
| 경로 | 전체 조회 GET 및 취소 URL 분리 | 컨트롤러에 잘못된 `@requestSecurity` 표현식 잔존 |
| 서비스 | 메서드 보안과 생성용 쓰기 트랜잭션 추가 | 취소가 `java.nio.file.AccessDeniedException` 사용 |
| 상태 | enum/sealed 모델, 승인 전이, version 필드 추가 | 반려·취소는 기존 ensurePending 경로여서 규칙 분산 |
| 비용 | 요청 DTO·엔티티·응답 DTO 추가 | 생성 서비스에서 estimatedCost 전달 누락 |
| 검증 | 문자열 길이·비용 제약, 바인딩 예외 처리 추가 | reason은 DTO 1023자/DB 기본 255자로 불일치, 반려 사유 길이 점검 |
| 오류 | 업무 충돌·잠금 충돌 핸들러 추가 | 광범위한 IllegalStateException → 409 핸들러 잔존 |
| 테스트 | 기존 contextLoads 1개 | 업무·보안·DTO·저장·동시성 검증 필요 |

실행한 기준 명령:

```bash
cd backend
./gradlew test --console=plain
```

결과: `:compileKotlin FAILED`, `ResourceRequestService.kt:33:13 No value passed for parameter 'estimatedCost'`. 테스트 실행 단계에는 도달하지 못했다. DB 기동·HTTP 동작은 아직 검증된 것으로 간주하지 않는다.

## 1. 컴파일과 비용 저장 연결

대상: `ResourceRequestService`, `CreateResourceRequestDto`, `ResourceRequest`, `ResourceResponseDto`.

서비스에서 엔티티를 만들 때 다음 인자를 연결한다.

```kotlin
estimatedCost = dto.estimatedCost
```

임의의 기본 비용으로 누락을 감추지 않는다. 입력한 BigDecimal이 생성·저장·flush·재조회·응답까지 유지되는지 확인한다. 이미 선택한 precision 14/scale 2와 DTO 정수 12자리/소수 2자리 제약을 맞춘다.

완료 기준: 컴파일 성공, 비용 필수 입력과 정상 저장 검증, 범위·소수 자릿수 초과는 400.

## 2. 인가 경계를 서비스로 정리

대상: `ResourceRequestController`, `ResourceRequestService`, `ResourceRequestSecurityService`.

서비스 메서드의 역할 검사와 인자 ID/인증 principal 일치 검사를 유지한다. 컨트롤러에서는 중복 인가와 `@requestSecurity` 참조를 제거하고 인증 ID를 전달한다. 더 이상 참조되지 않는 소유권 조회 빈은 사용처를 확인해 정리한다.

서비스의 취소 예외를 `org.springframework.security.access.AccessDeniedException`으로 바꾼다. 소유권은 조회한 신청서를 대상으로 쓰기 트랜잭션 안에서 확인한다. 개발자의 관리자 기능 접근, 감사자의 모든 쓰기, 타인 신청 취소와 타인 ID 위장은 거부해야 한다.

완료 기준: 실제 Spring 서비스 프록시 호출과 HTTP 모두에서 허용/거부 확인. 타인 취소는 Spring 예외로 처리되어 JSON 403을 반환하고 DB는 변경되지 않음.

## 3. 상태 전이를 한 경로로 통일

대상: `RequestStatus.kt`의 sealed 모델·nextState, `ResourceRequest`의 approve/reject/cancel.

승인에 적용한 `nextState(toState(), command)` 패턴을 반려·취소에도 적용한다. 반려에는 저장된 관리자 ID와 유효한 사유를 전달하고 처리자·사유를 함께 반영한다. 전이 계산이 성공한 뒤에만 필드를 변경한다. 기존 ensurePending이 더 이상 필요 없으면 제거한다.

enum은 저장 표현, sealed 모델은 상태별 데이터·전이 표현으로 유지한다. 별도 수정 가능한 상태 필드를 추가하지 않는다. `CANCELLED`를 유지하고 만료와 합치지 않는다. 모든 완료 상태의 추가 명령은 업무 충돌이어야 한다.

완료 기준: 대기의 세 전이는 성공. 완료 상태의 세 명령은 실패하고 기존 처리자·사유·상태가 유지됨. 테스트는 순수 함수와 엔티티 반영을 각각 확인함.

## 4. 입력 계약·DB 제약·예외 구분 일치

사용자가 설정한 신청 목적 1023자를 유지하는 방향을 우선해 DTO와 엔티티 컬럼 길이를 맞춘다. 스펙은 255자, 반려 사유는 명시한 DTO·도메인·컬럼 한도가 일치하도록 정한다. 단순 NotBlank만으로 긴 입력이 DB 오류가 되지 않게 한다.

업무 충돌은 `RequestStateConflictException`, 동시성 충돌은 낙관적 잠금 예외로 409를 반환한다. 기존 IllegalStateException 전체를 409로 분류하는 핸들러는 제거해 내부 불변식 오류와 분리한다. 바인딩 실패·Bean Validation 실패는 공통 400 형태를 유지하고 민감한 원문을 반환하지 않는다.

완료 기준: 누락/null/공백/길이/enum/금액 경계의 400, 권한 403, 업무·잠금 충돌 409를 구분. 내부 오류를 409로 오인하지 않음.

## 5. JPA 스키마와 동시성 확인

현재 `@Version`과 비용 필드를 활용한다. 테스트 전용 DB에서 기존 필드의 변경 감지, 비용 컬럼, version 증가와 실제 경쟁 갱신을 확인한다. 두 트랜잭션이 같은 version을 읽도록 동기화해 하나만 커밋되는지 검증한다. 순차적인 재승인 테스트를 동시성 검증으로 대체하지 않는다.

실제 MySQL 접근이 불가능하면 격리된 테스트 설정으로 가능한 검증을 수행하고, MySQL에 의존하는 미검증 사항을 명시한다. 필요한 테스트 의존성은 최소 범위로 추가한다. 기존 데이터가 있는 DB는 임의 초기화하지 않는다.

스키마 변경은 현재 프로젝트 방식을 확인해 안내한다. version 초기값과 컬럼 길이 변경은 명시하되, 과거 행의 알 수 없는 예상 비용을 임의의 0원으로 채우지 않는다. 실제 데이터 백필이 필요하면 정책과 적용 절차를 남긴다.

완료 기준: 저장·재조회·경쟁 갱신 결과와 실행 DB 종류를 기록하고, 기존 행에 대한 스키마/비용 보완 절차를 구분함.

## 제출할 결과

작업자는 [TASKS.md](TASKS.md)를 근거와 함께 갱신하고, 변경 파일·핵심 동작·실행 명령과 결과·남은 제약을 한국어로 보고한다. 계획 작성만으로 작업을 종료하지 않고 코드 수정과 필요한 검증까지 수행한다. 검증하지 못한 항목은 완료로 표시하지 않는다.
