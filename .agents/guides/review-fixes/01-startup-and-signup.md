# 1. 기동과 회원가입

## nullable User를 저장하는 경로 수정

대상: [AuthService.kt](../../../backend/src/main/kotlin/com/programmers/be14/cloudportal_practice_261018/domain/auth/service/AuthService.kt).

현재 `passwordEncoder.encode(...)?.let { User(...) }`의 결과는 `User?`다. JPA `save()`에는 non-null 엔티티가 필요하다. 비밀번호 인코딩 결과의 null 처리를 먼저 끝낸 다음 `User`를 생성한다. 아래는 `signUp()` 교체 예시다.

```kotlin
@Transactional
fun signUp(request: SignUpRequest): Long {
    require(!userRepository.existsByEmail(request.email)) {
        "이미 가입된 이메일입니다."
    }

    val passwordHash = checkNotNull(passwordEncoder.encode(request.password)) {
        "비밀번호 인코딩 결과가 없습니다."
    }
    val user = User(
        email = request.email,
        passwordHash = passwordHash,
        username = request.username,
        role = Role.DEVELOPER
    )
    return checkNotNull(userRepository.save(user).id) {
        "저장된 사용자의 ID가 없습니다."
    }
}
```

`Role`은 `domain.user.entity.Role`을 import한다. 이 코드에서 nullable은 인코더 결과와 저장 전 ID처럼 실제 경계에만 남고, 저장할 엔티티 자체는 항상 존재한다. 이메일 중복의 사전 조회는 사용자 안내용이고, 동시 가입의 최종 중복 방지는 기존 DB unique 제약이 맡는다.

## 가입 시 역할은 서버가 결정

대상: [AuthDtos.kt](../../../backend/src/main/kotlin/com/programmers/be14/cloudportal_practice_261018/domain/auth/dto/AuthDtos.kt).

`SignUpRequest`의 `role` 프로퍼티와 사용하지 않는 `Role` import를 제거한다. 기존 세 필드의 검증은 유지한다.

```kotlin
data class SignUpRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    @field:NotBlank val username: String
)
```

서비스도 위 예시처럼 `Role.DEVELOPER`를 직접 지정한다. DTO만 바꾸고 서비스의 `request.role` 참조를 남기면 컴파일되지 않는다. `role`이 포함된 JSON의 거부/무시 여부와 관계없이, 공개 가입으로 생성되는 사용자 역할이 항상 `DEVELOPER`인지 확인한다. 관리자·감사자 부여 경로는 별도 권한으로 설계한다.

## JWT 설정 placeholder 수정

대상: [JwtTokenProvider.kt](../../../backend/src/main/kotlin/com/programmers/be14/cloudportal_practice_261018/global/security/JwtTokenProvider.kt).

만료 시간의 `@Value` 문자열에 닫는 `}`가 빠져 있다. 해당 생성자 파라미터를 다음처럼 수정한다. `\$`는 Kotlin 문자열 보간을 막고, `${...}`는 Spring이 설정 값으로 해석한다.

```kotlin
@Value("\${jwt.access-token-expiration-seconds}")
private val accessTokenExpirationSeconds: Long
```

기존 `$$"${...}"` 형태를 유지해도 되지만, 어느 형태든 Spring placeholder의 괄호가 완성되어야 한다.

## HTTP 경로 분리

대상: [ResourceRequestController.kt](../../../backend/src/main/kotlin/com/programmers/be14/cloudportal_practice_261018/domain/request/controller/ResourceRequestController.kt).

| 행위 | HTTP 경로 |
| --- | --- |
| 생성 | `POST /api/requests` |
| 본인 목록 | `GET /api/requests/me` |
| 전체 목록 | `GET /api/requests?status=PENDING` |
| 취소 | `POST /api/requests/{id}/cancel` |
| 승인 | `POST /api/requests/{id}/approve` |
| 반려 | `POST /api/requests/{id}/reject` |

전체 조회에 `@GetMapping`을 추가하고, 취소에 경로 변수와 구별되는 URL을 부여한다. 아래 취소 메서드는 다음 장의 서비스 시그니처 변경과 함께 적용한다.

```kotlin
@GetMapping
fun getAllRequests(
    @RequestParam(required = false) status: RequestStatus?
): ResponseEntity<List<ResourceResponseDto>> =
    ResponseEntity.ok(resourceRequestService.getAllRequests(status))

@PostMapping("/{id}/cancel")
fun cancelRequest(
    @PathVariable id: Long,
    @AuthenticationPrincipal principal: UserPrincipal
): ResponseEntity<Unit> {
    resourceRequestService.cancelRequest(principal.id, id)
    return ResponseEntity.noContent().build()
}
```

동일한 URL·HTTP 메서드로 등록되는 생성과 취소를 분리하면 기동 시 매핑 충돌이 사라진다. 매핑 조건의 의미는 [Spring MVC 공식 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html)를 참고한다.

## 완료 기준

`backend`에서 `./gradlew compileKotlin --console=plain`이 성공해야 한다. 2장의 인가 변경까지 연결한 뒤 테스트용 DB·JWT 설정으로 컨텍스트를 기동하고, 위 경로가 각각 올바른 메서드에 연결되는지 확인한다.
