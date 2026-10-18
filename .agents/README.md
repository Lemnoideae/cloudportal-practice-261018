# 에이전트 문서 진입점

이 프로젝트는 Java → Kotlin 마이그레이션을 대비해 Kotlin과 Spring Boot, 특히 Spring Security를 학습하는 사내 클라우드 인프라 자원 신청·승인 포털이다.

모든 작업 전에 [프로젝트 목적과 범위](project/overview.md)를 읽는다. 이후 아래 표에서 작업에 해당하는 문서를 읽는다. 여러 영역에 걸친 변경은 해당 문서를 함께 적용한다.

| 작업 | 읽을 문서 |
| --- | --- |
| 신청서·승인/반려 이력·카탈로그, 상태 전이 | [도메인과 상태 머신](domain/resource-requests.md) |
| 로그인·토큰·역할·소유권·조회 범위 | [인증과 인가](security/authentication-authorization.md) |
| 엔티티·연관관계·영속성·트랜잭션 | [Kotlin–JPA 매핑](kotlin/jpa-mapping.md) |
| 요청/응답 DTO·입력 검증·null·오류 응답 | [Null Safety와 DTO 검증](kotlin/dto-validation.md) |
| 기능 구현·리팩터링·Java 코드 전환·검증 | [구현과 완료 기준](workflow/implementation.md) |
| 리뷰 지적 사항 수정·수정 코드 예시·적용 순서 | [리뷰 수정 가이드](guides/review-fixes/README.md) |
| 직접 수정한 코드의 후속 점검·비용/DTO 단계까지 보완 | [보완 계획과 할 일](plans/repair-through-cost/README.md) |

문서는 목표와 판단 기준을 정의한다. 현재 구현 여부·라이브러리 버전·실행 명령은 소스와 빌드 설정에서 확인한다. 문서의 **제안**과 **미결정**은 확정 요구사항과 구분하고, 정책이 결정되면 해당 원본 문서 한 곳을 갱신한다.
