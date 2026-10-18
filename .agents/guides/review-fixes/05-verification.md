# 5. 적용 후 검증

문서의 예시는 설명용 코드이며, 현재 애플리케이션에 적용해 컴파일·실행한 결과가 아니다. 각 단계를 적용한 뒤 아래 증거를 확보한다. 기존 리뷰에서 확인된 컴파일 실패와 수정 후 결과를 구분해 기록한다.

## 빌드와 기동

`backend`에서 다음 명령을 실행한다.

```bash
./gradlew compileKotlin --console=plain
./gradlew test --console=plain
```

MySQL과 JWT 설정은 별도의 테스트 환경에서 준비한다. `contextLoads()`의 성공은 빈 생성과 매핑 등록을 확인하며, 역할·소유권·상태 규칙의 검증은 아래 테스트가 별도로 담당한다.

## 시나리오와 완료 기준

| 영역 | 입력·실행 | 확인할 결과 |
| --- | --- | --- |
| 회원가입 | 일반 가입, 관리자 role을 포함한 요청 | 일반 가입은 DEVELOPER. 관리자 요청은 거부되거나 역할이 무시되고 DEVELOPER 저장 |
| 인증 | 토큰 누락·만료·서명 변조로 보호 API 호출 | 401, 업무 데이터 변경 없음 |
| 역할 | DEVELOPER의 승인/반려, AUDITOR의 모든 쓰기 | 403, 상태·이력 변경 없음 |
| 경로 | 생성·취소, 본인 목록·전체 목록을 각각 호출 | 매핑 충돌 없이 의도한 메서드 실행 |
| 생성 | 정상 신청 후 별도 트랜잭션에서 재조회 | 실제 INSERT와 필드 값 확인 |
| 소유권 | 타인의 대기 신청 수정·취소 | 403, 원래 내용과 상태 유지 |
| 호출 경계 | 다른 서비스에서 주입받은 서비스 빈에 타인 ID 전달 | 메서드 인가에서 거부 |
| 상태 | 대기 신청의 승인·반려·취소 | 각각 의도한 완료 상태 |
| 완료 상태 | 이미 승인·반려·취소된 신청에 재결정 | 409, 기존 처리자·사유 유지 |
| 충돌 | 별도 트랜잭션 두 개로 같은 version을 읽고 동시에 결정 | 하나만 커밋, 다른 하나는 충돌 |
| 이력 | 이력 저장 실패 또는 결정의 버전 충돌 유도 | 신청 상태와 결정 이력이 함께 롤백 |
| DTO | 필드 누락·null·공백·길이 초과·잘못된 enum·비용 범위 초과 | 400, 내부 예외·민감 입력 노출 없음 |
| 카탈로그 | 참조된 항목 수정·삭제/비활성화 | 기존 신청의 스냅샷·이력 유지 |

## 도메인 테스트 예시

다음은 3장의 최소 상태 수정 단계에 대한 테스트 메서드다. `admin`과 `anotherAdmin`은 서로 다른 저장 ID의 User, `pendingRequest`는 개발자의 PENDING ResourceRequest로 준비한다. 반려·취소 완료 상태도 같은 방식으로 추가한다.

```kotlin
@Test
fun `승인된 신청은 다시 승인할 수 없다`() {
    pendingRequest.approve(admin)
    assertThrows<RequestStateConflictException> {
        pendingRequest.approve(anotherAdmin)
    }
    assertEquals(RequestStatus.APPROVED, pendingRequest.status)
    assertEquals(admin.id, pendingRequest.approver?.id)
}
```

사용하는 API는 `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.assertThrows`, `kotlin.test.assertEquals`다. sealed 전환 후에는 순수 전이 함수를 테스트하고, 엔티티 매핑 테스트로 저장 상태와 처리 정보를 확인한다.

## 보안·영속성 테스트의 주의점

서비스를 직접 `ResourceRequestService(...)`로 생성하면 메서드 보안과 트랜잭션 프록시를 검증하지 못한다. Spring에서 주입받은 빈으로 호출한다. 현재 표현식의 `principal.id`를 평가하려면 테스트 인증 정보에도 실제 `UserPrincipal`을 넣는다. 기본 `@WithMockUser`의 principal은 이 타입과 다르다. [Spring Security 테스트 문서](https://docs.spring.io/spring-security/reference/servlet/test/method.html)

실제 JWT 필터 검증은 서명한 정상/만료 토큰과 변조 토큰을 HTTP에 넣어 수행한다. 보안 컨텍스트를 직접 채우는 역할 테스트만으로 JWT 검증까지 통과했다고 판단하지 않는다.

JPA 검증은 flush 후 영속성 컨텍스트를 비우고 재조회한다. version 충돌 테스트는 테스트 전체를 감싼 단일 트랜잭션이 아니라 독립 트랜잭션과 동기화 지점을 사용한다. 충돌은 commit에서 발생할 수 있으므로 실제 HTTP의 409 응답도 확인한다.

현재 `GlobalExceptionHandler`에는 올바른 Spring `AccessDeniedException` import가 있다. 이 상태를 유지하고, 메서드 인가 실패가 의도한 JSON 403으로 반환되는지 확인한다. 필터 단계의 401/403 응답 형식은 Security 설정의 entry point와 access denied handler에서 별도로 맞춘다.
