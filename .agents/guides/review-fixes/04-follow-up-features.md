# 4. 후속 기능 구현

이 장은 리뷰 오류를 고친 다음의 구현 예시다. 신규 비용·이력·카탈로그의 상세 정책은 [도메인 문서](../../domain/resource-requests.md)에서 확정한다. 아래 코드의 새 필드·타입·저장소는 아직 소스에 없으며, 관련 스키마와 DTO·응답을 함께 연결해야 한다.

## 예상 비용과 DTO 검증

예상 비용이 신청자 입력인지 카탈로그 기반 계산인지 먼저 정한다. 아래는 **신청자가 예상 금액을 입력한다는 제안**이다. 통화와 비용 산정 기간은 별도로 명시한다.

`CreateResourceRequestDto` 생성자에 다음 필드를 추가한다. `BigDecimal`은 `java.math.BigDecimal`, 제약은 `jakarta.validation.constraints`에서 import한다.

```kotlin
@field:DecimalMin("0.00")
@field:Digits(integer = 12, fraction = 2)
val estimatedCost: BigDecimal
```

엔티티의 대응 필드는 다음과 같이 매핑한다. 기존 행의 비용은 의미를 정해 백필한 뒤 필수 제약을 적용한다.

```kotlin
@field:Column(nullable = false, precision = 14, scale = 2)
var estimatedCost: BigDecimal = estimatedCost
    protected set
```

위 엔티티 코드는 **클래스 본문 프로퍼티 선언 형태**다. `ResourceRequest` 생성자에 `estimatedCost: BigDecimal` 파라미터를 추가해 초기값을 받는다. 서비스 생성자 호출과 `ResourceResponseDto`도 함께 갱신한다.

기존 spec·reason은 기본 문자열 컬럼 한도를 고려해 DTO에 `@field:Size(max = 255)`를 추가하거나, 더 긴 입력이 필요하면 컬럼 길이를 함께 바꾼다. 입력 DTO의 변경이 필요 없는 프로퍼티는 `val`로 선언한다. non-null 타입도 공백·길이·금액 범위를 보장하지는 않는다.

필수 필드 누락이나 null, 잘못된 enum은 바인딩 단계에서 실패할 수 있다. 기존 Bean Validation 핸들러와 별도로 `GlobalExceptionHandler`에 다음 처리를 추가한다.

```kotlin
@ExceptionHandler(HttpMessageNotReadableException::class)
fun handleUnreadableBody(
    e: HttpMessageNotReadableException
): ResponseEntity<ErrorResponse> = ResponseEntity.badRequest()
    .body(ErrorResponse("INVALID_REQUEST", "요청 형식과 필수 입력값을 확인해주세요."))
```

`org.springframework.http.converter.HttpMessageNotReadableException`을 import한다. 원문 예외 메시지나 비밀번호를 응답하지 않는다. 컨트롤러 파라미터에 직접 제약을 추가하면 `HandlerMethodValidationException`도 처리한다. [Spring MVC 검증 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)

## 대기 신청 수정 API

**제안**: 우선 `PUT /api/requests/{id}`로 수정 가능한 필드의 전체 교체를 지원한다. 생성과 수정 DTO는 각각 정의하되 동일한 필드 제약을 유지한다. 부분 수정이 필요하면 필드 누락과 명시적 null을 구분하는 별도 계약을 정한다.

흐름은 다음과 같다.

```text
UpdateResourceRequestDto 검증
→ DEVELOPER 역할 및 principal.id와 서비스 userId 일치 확인
→ 쓰기 트랜잭션에서 조회 및 소유권 확인
→ request.update(...)에서 PENDING 검증
→ 수정 가능한 스펙·목적·비용만 변경
→ version 검사와 함께 커밋
```

새 도메인 메서드의 전이 검증도 3장의 방식에 맞춘다. 최소 수정 단계에서는 `ensurePending()`, sealed 전환 후에는 대기 상태에 대한 수정 연산을 정의한다. ID·신청자·현재 상태·처리자 필드를 수정 DTO에 노출하지 않는다. 승인과 수정이 경쟁할 때도 version으로 한쪽을 거부한다.

## 승인·반려 이력의 원자성

처리자와 마지막 사유만 저장하는 현재 엔티티는 이력 모델이 아니다. 신규 `RequestDecision` 엔티티와 저장소를 추가한다. 다음은 새 모델의 **필드 계약**이다.

```kotlin
data class DecisionRecord(
    val requestId: Long,
    val actorId: Long,
    val previousStatus: RequestStatus,
    val nextStatus: RequestStatus,
    val reason: String?,
    val decidedAt: Instant
)
```

이 data class는 이력 값의 표현이며 JPA 엔티티로 직접 사용하지 않는다. 저장 엔티티는 일반 class로 만들고, enum 문자열 매핑·필수 컬럼·참조 관계를 정의한다. `Instant`와 `Clock`은 `java.time`에서 import한다. 기존 설정 클래스에 다음 빈을 등록하고 서비스 생성자로 Clock을 주입한다. 테스트에서는 고정 Clock으로 바꿔 시각을 재현한다.

```kotlin
@Bean
fun clock(): Clock = Clock.systemUTC()
```

승인 서비스의 한 트랜잭션 안에서 다음 순서로 저장한다. 아래 `decisionRepository.append(record)`는 **새로 정의할 저장소 계약 예시**이며 현재 저장소에 존재하는 메서드가 아니다.

```kotlin
val request = findRequest(requestId)
val previousStatus = request.status
request.approve(admin)
decisionRepository.append(
    DecisionRecord(
        requestId = checkNotNull(request.id),
        actorId = adminId,
        previousStatus = previousStatus,
        nextStatus = request.status,
        reason = null,
        decidedAt = clock.instant()
    )
)
```

반려도 같은 패턴으로 사유를 기록한다. 기존 서비스에 저장소와 Clock을 주입하고, `append`는 `REQUIRED`로 외부 트랜잭션에 참여시킨다. 독립 커밋이나 비동기 저장으로 분리하지 않는다. 이력 저장 실패 또는 version 충돌이면 신청 변경과 이력을 함께 롤백한다.

`GET /api/requests/{id}/decisions`를 추가할 때도 신청서와 같은 조회 범위를 적용한다. 이력 정정·삭제 허용 여부는 감사 정책을 확정한 후 구현한다.

## 카탈로그와 신청 스냅샷

카탈로그는 문자열 spec 입력과 별도 모델이다. 다음은 API 계약의 초기 제안이다.

| API | 역할 | 책임 |
| --- | --- | --- |
| `GET /api/catalog` | 인증된 사용자 | 신청 가능한 스펙 조회 |
| `POST /api/catalog` | DEVOPS_ADMIN | 스펙·가격 정보 등록 |
| `PUT /api/catalog/{id}` | DEVOPS_ADMIN | 스펙·가격 정보 수정 |
| `DELETE /api/catalog/{id}` | DEVOPS_ADMIN | 참조 정책에 따른 삭제 또는 비활성화 |

신청 DTO에서 `catalogSpecId`를 받아 활성 항목인지 서비스에서 검증하고, 선택한 인스턴스 타입·스펙·비용 조건을 신청서에 복사하는 구조를 권장한다. 이후 카탈로그 가격을 바꿔도 기존 신청의 승인 근거가 바뀌지 않아야 한다. 카탈로그 가격 계산 방식을 선택하면 앞의 사용자 입력 비용 예시도 그 계약에 맞게 교체한다.

감사자의 카탈로그 쓰기는 서비스 인가로 거부하고, 참조 중인 항목의 삭제가 기존 신청·이력을 훼손하지 않도록 검증한다.
