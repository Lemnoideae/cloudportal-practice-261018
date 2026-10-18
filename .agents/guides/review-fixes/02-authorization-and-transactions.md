# 2. 서비스 인가와 트랜잭션

## 역할은 서비스 진입점, 소유권은 변경 트랜잭션 안에서

대상: [ResourceRequestService.kt](../../../backend/src/main/kotlin/com/programmers/be14/cloudportal_practice_261018/domain/request/service/ResourceRequestService.kt).

컨트롤러는 인증된 principal의 ID와 DTO를 전달하고, 서비스가 권한과 업무 처리를 책임지도록 바꾼다. 필터는 인증만 수행하며, 신청서의 소유권을 판단하지 않는다.

아래 코드는 기존 `ResourceRequestService`의 교체 예시다. 현재 DTO와 엔티티를 사용하며, 3장에서 전이와 동시성 보호를 보완한다. 필요한 추가 import는 Spring의 `PreAuthorize`, `AccessDeniedException`이다.

```kotlin
@Service
@Transactional(readOnly = true)
class ResourceRequestService(
    private val resourceRequestRepository: ResourceRequestRepository,
    private val userRepository: UserRepository
) {
    @PreAuthorize("hasRole('DEVELOPER') and #userId == principal.id")
    @Transactional
    fun createRequest(userId: Long, dto: CreateResourceRequestDto): Long {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw NoSuchElementException("사용자를 찾을 수 없습니다.")
        val request = ResourceRequest(
            resourceType = dto.resourceType,
            spec = dto.spec,
            reason = dto.reason,
            requester = user
        )
        return checkNotNull(resourceRequestRepository.save(request).id)
    }

    @PreAuthorize("isAuthenticated() and #userId == principal.id")
    fun getMyRequests(userId: Long): List<ResourceResponseDto> =
        resourceRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(userId)
            .map { ResourceResponseDto.from(it) }

    @PreAuthorize("hasAnyRole('DEVOPS_ADMIN', 'AUDITOR')")
    fun getAllRequests(status: RequestStatus?): List<ResourceResponseDto> {
        val requests = if (status == null) {
            resourceRequestRepository.findAll()
        } else {
            resourceRequestRepository.findAllByStatusOrderByCreatedAtDesc(status)
        }
        return requests.map { ResourceResponseDto.from(it) }
    }

    @PreAuthorize("hasRole('DEVELOPER') and #userId == principal.id")
    @Transactional
    fun cancelRequest(userId: Long, requestId: Long) {
        val request = findRequest(requestId)
        if (request.requester.id != userId) {
            throw AccessDeniedException("본인의 신청서만 취소할 수 있습니다.")
        }
        request.cancel()
    }

    @PreAuthorize("hasRole('DEVOPS_ADMIN') and #adminId == principal.id")
    @Transactional
    fun approveRequest(adminId: Long, requestId: Long) {
        val admin = userRepository.findByIdOrNull(adminId)
            ?: throw NoSuchElementException("처리자를 찾을 수 없습니다.")
        findRequest(requestId).approve(admin)
    }

    @PreAuthorize("hasRole('DEVOPS_ADMIN') and #adminId == principal.id")
    @Transactional
    fun rejectRequest(adminId: Long, requestId: Long, dto: RejectRequestDto) {
        val admin = userRepository.findByIdOrNull(adminId)
            ?: throw NoSuchElementException("처리자를 찾을 수 없습니다.")
        findRequest(requestId).reject(admin, dto.reason)
    }

    private fun findRequest(id: Long): ResourceRequest =
        resourceRequestRepository.findByIdOrNull(id)
            ?: throw NoSuchElementException("신청서를 찾을 수 없습니다.")
}
```

### 코드에서 책임이 나뉘는 지점

| 코드 | 책임 |
| --- | --- |
| `hasRole(...)` | 현재 인증 정보의 역할 검사 |
| `#userId == principal.id` | 호출자가 다른 사용자 ID로 행동하는 것 차단 |
| `request.requester.id != userId` | 조회한 자원의 소유권 검사 |
| `request.cancel()` 등 | 현재 상태에서 해당 전이가 가능한지 검사 |
| 메서드의 `@Transactional` | 조회·변경·후속 이력 저장을 하나의 쓰기 트랜잭션으로 묶기 |

권한은 메서드 인자의 역할 값이 아니라 Spring의 인증 정보에서 판단한다. 현재 `UserPrincipal`의 ID와 서비스 인자를 비교하므로, 다른 서비스가 타인의 ID를 넘겨도 허용되지 않는다.

이 예시는 본인 목록 조회와 관리자·감사자 전체 조회를 사용한다. 조직별 범위가 필요하면 [조회 정책](../../security/authentication-authorization.md)을 먼저 확정하고 DB 조회 조건에 반영한다.

## 컨트롤러와 빈 정리

서비스로 옮긴 동일한 역할 검사는 컨트롤러에서 제거한다. 취소의 `@PreAuthorize("@requestSecurity.isOwner(...)")`도 제거한다. [1장의 취소 예시](01-startup-and-signup.md)처럼 인증된 ID를 서비스에 전달한다.

이 방식은 잘못된 빈 이름을 고치는 데서 그치지 않고, 소유권 조회와 실제 변경을 같은 트랜잭션으로 모은다. `ResourceRequestSecurityService`는 다른 참조가 없음을 확인한 뒤 제거할 수 있다. 기존 방식을 유지해야 한다면 빈 이름을 일치시켜야 하지만, 변경 서비스에서도 소유권 검사는 필요하다.

기존 `@EnableMethodSecurity`를 유지한다. `@PreAuthorize`와 `@Transactional`은 Spring 빈의 프록시를 통해 호출되어야 한다. 같은 객체 안에서 `this.cancelRequest(...)`를 호출하거나 서비스를 직접 생성하면 이 경계가 적용되지 않는다. 배치의 시스템 권한은 별도 진입점으로 설계한다. [Spring 메서드 보안 문서](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)

## 생성에 쓰기 트랜잭션이 필요한 이유

클래스의 읽기 전용 설정은 조회 메서드의 기본값이다. 메서드 수준 `@Transactional`은 생성·취소·승인·반려를 쓰기로 지정한다. 저장소의 `save()`에 트랜잭션이 있더라도 이미 시작한 외부 읽기 전용 트랜잭션을 쓰기로 바꾸지 않는다. [Spring Data JPA 트랜잭션 문서](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)

읽기 전용은 성능·실행 방식에 대한 힌트이며, `AUDITOR`의 쓰기 금지는 인가로 보장한다. 이 예시에서 감사자는 모든 쓰기 서비스의 역할 검사에서 거부된다.

## 완료 기준

HTTP 요청뿐 아니라 주입받은 서비스 빈 직접 호출에서도 감사자의 쓰기, 개발자의 승인, 타인 ID를 전달한 호출이 거부되어야 한다. 본인 대기 신청 취소와 신청 생성은 실제 DB에 반영되어야 한다. DTO 매핑은 서비스 트랜잭션 안에서 수행해 지연 로딩 경계를 유지한다.
