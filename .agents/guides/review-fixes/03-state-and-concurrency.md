# 3. 상태 전이와 동시성

## 먼저 현재 엔티티의 전이 복구

대상: [ResourceRequest.kt](../../../backend/src/main/kotlin/com/programmers/be14/cloudportal_practice_261018/domain/request/entity/ResourceRequest.kt).

`approve()`는 상태를 검사하지 않고, `cancel()`은 `REJECTED`를 검사한다. 세 행위 모두 대기 상태에서만 수행하도록 기존 메서드를 교체한다. 아래 예시는 현재 `CANCELLED`를 유지한다. 만료와 취소의 구분은 [도메인 정책](../../domain/resource-requests.md)을 따른다.

먼저 `domain/request/exception/RequestStateConflictException.kt`에 업무 충돌 타입을 추가한다.

```kotlin
class RequestStateConflictException(message: String) : RuntimeException(message)
```

엔티티에서 이 예외를 import하고 다음 메서드를 적용한다.

```kotlin
private fun ensurePending() {
    if (status != RequestStatus.PENDING) {
        throw RequestStateConflictException("대기 중인 신청서만 처리할 수 있습니다.")
    }
}

fun approve(admin: User) {
    ensurePending()
    status = RequestStatus.APPROVED
    approver = admin
}

fun reject(admin: User, reason: String) {
    ensurePending()
    require(reason.isNotBlank()) { "반려 사유는 필수입니다." }
    status = RequestStatus.REJECTED
    approver = admin
    rejectReason = reason
}

fun cancel() {
    ensurePending()
    status = RequestStatus.CANCELLED
}
```

소유권과 역할은 2장의 서비스가 확인하고, 엔티티는 상태 불변식을 지킨다. 상태와 처리 정보는 검증을 마친 뒤 변경한다. 승인 상태에서 다시 `approve()`를 호출해도 처리자가 바뀌지 않아야 한다.

## 동시 결정은 버전으로 보호

엔티티 본문에 다음 필드를 추가한다. `jakarta.persistence.Version`을 사용한다.

```kotlin
@field:Version
@field:Column(nullable = false)
var version: Long = 0
    protected set
```

기존 테이블에도 version 컬럼과 기존 행의 초기값이 필요하다. 다음은 MySQL 스키마 변경의 예시이며, 프로젝트의 마이그레이션 방식에 맞춰 적용한다.

```sql
ALTER TABLE resource_requests
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

두 트랜잭션이 같은 version을 읽어도 먼저 갱신한 쪽만 성공하고, 뒤의 갱신은 충돌한다. `ensurePending()`만으로는 두 트랜잭션이 각각 읽은 대기 상태를 구별할 수 없다. 반대로 버전만으로 순차적인 재승인을 막을 수도 없다. 상태 규칙과 버전 검사가 함께 필요하다. [Hibernate 낙관적 잠금 문서](https://docs.hibernate.org/orm/7.1/userguide/html_single/#locking-optimistic)

승인·반려·취소는 충돌 시 자동 재시도하지 않고 호출자에게 다시 조회하도록 알린다. 후속 이력 저장도 같은 트랜잭션에서 수행하면 실패한 결정의 이력이 함께 롤백된다.

## 업무 상태 충돌과 잠금 충돌을 409로 변환

대상: [GlobalExceptionHandler.kt](../../../backend/src/main/kotlin/com/programmers/be14/cloudportal_practice_261018/global/error/GlobalExceptionHandler.kt).

기존의 광범위한 `IllegalStateException` 핸들러를 아래 업무 예외 핸들러로 교체하고, 잠금 예외 핸들러를 추가한다. `RequestStateConflictException`과 `org.springframework.orm.ObjectOptimisticLockingFailureException`을 import한다.

```kotlin
@ExceptionHandler(RequestStateConflictException::class)
fun handleStateConflict(
    e: RequestStateConflictException
): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.CONFLICT)
    .body(ErrorResponse("INVALID_TRANSITION", e.message ?: "처리할 수 없는 상태입니다."))

@ExceptionHandler(ObjectOptimisticLockingFailureException::class)
fun handleConcurrentDecision(
    e: ObjectOptimisticLockingFailureException
): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.CONFLICT)
    .body(ErrorResponse("CONCURRENT_MODIFICATION", "다른 처리가 먼저 반영되었습니다. 다시 조회해주세요."))
```

업무 충돌 타입을 구분하면 `checkNotNull(saved.id)` 같은 내부 불변식 오류를 사용자 상태 충돌로 오인하지 않는다. 잠금 오류는 flush 또는 커밋에서 발생할 수 있으므로 서비스 본문 안의 try/catch에만 의존하지 않는다. [Spring 잠금 예외 문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/ObjectOptimisticLockingFailureException.html)

## 다음 단계: enum과 sealed class로 전이 분리

위 수정이 동작한 뒤 도메인 순수 함수로 옮기는 예시다. 이 단계에서는 기존 `ensurePending()` 기반 전이를 아래 전이 함수 호출로 교체한다. enum은 저장 코드이고, sealed 모델은 상태별 데이터와 명령의 표현이다.

`RequestStatus`에 `EXPIRED`를 추가하되, 아래 `Expire` 명령의 실제 호출 조건·시스템 실행 권한은 만료 정책 확정 후 구현한다. 이 예시의 `CANCELLED` 유지는 현재 데이터와의 호환을 위한 제안이다.

```kotlin
sealed class RequestState(val status: RequestStatus) {
    data object Pending : RequestState(RequestStatus.PENDING)
    data class Approved(val actorId: Long) : RequestState(RequestStatus.APPROVED)
    data class Rejected(val actorId: Long, val reason: String) : RequestState(RequestStatus.REJECTED)
    data object Cancelled : RequestState(RequestStatus.CANCELLED)
    data object Expired : RequestState(RequestStatus.EXPIRED)
}

sealed class RequestCommand {
    data class Approve(val actorId: Long) : RequestCommand()
    data class Reject(val actorId: Long, val reason: String) : RequestCommand()
    data object Cancel : RequestCommand()
    data object Expire : RequestCommand()
}

fun nextState(current: RequestState, command: RequestCommand): RequestState =
    when (current) {
        RequestState.Pending -> when (command) {
            is RequestCommand.Approve -> RequestState.Approved(command.actorId)
            is RequestCommand.Reject -> {
                require(command.reason.isNotBlank()) { "반려 사유는 필수입니다." }
                RequestState.Rejected(command.actorId, command.reason)
            }
            RequestCommand.Cancel -> RequestState.Cancelled
            RequestCommand.Expire -> RequestState.Expired
        }
        is RequestState.Approved, is RequestState.Rejected,
        RequestState.Cancelled, RequestState.Expired ->
            throw RequestStateConflictException("완료된 신청서는 다시 처리할 수 없습니다.")
    }
```

DB 엔티티는 현재처럼 enum과 처리자·사유를 저장한다. 엔티티 안에 다음 변환을 두고, 별도의 수정 가능한 `state` 필드를 추가하지 않는다.

```kotlin
private fun toState(): RequestState = when (status) {
    RequestStatus.PENDING -> RequestState.Pending
    RequestStatus.APPROVED -> RequestState.Approved(checkNotNull(approver?.id))
    RequestStatus.REJECTED -> RequestState.Rejected(
        checkNotNull(approver?.id), checkNotNull(rejectReason)
    )
    RequestStatus.CANCELLED -> RequestState.Cancelled
    RequestStatus.EXPIRED -> RequestState.Expired
}

fun approve(admin: User) {
    val next = nextState(toState(), RequestCommand.Approve(checkNotNull(admin.id)))
    status = next.status
    approver = admin
}
```

`reject()`와 `cancel()`도 같은 방식으로 기존 상태를 복원해 명령을 전달하고 반환된 상태를 적용한다. 반려에서는 처리자·사유를 함께 저장한다. 상태 검사는 순수 전이 함수 한 곳이 담당하고, JPA 엔티티는 저장 매핑을 담당한다. sealed 계층은 JPA 엔티티 상속으로 매핑하지 않는다. [Kotlin sealed 문서](https://kotlinlang.org/docs/sealed-classes.html)

## 완료 기준

대기 신청의 승인·반려·취소는 성공하고, 모든 완료 상태에서 세 명령은 충돌해야 한다. 동시성 검증은 별도 트랜잭션 두 개가 같은 version을 읽도록 동기화한 뒤 수행한다. 단순한 순차 호출은 동시성 테스트가 아니다.
