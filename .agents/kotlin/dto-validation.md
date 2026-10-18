# Null Safety와 DTO 검증

## 검증 경계

| 경계 | 확인할 내용 |
| --- | --- |
| Kotlin 타입 | 필수 값은 non-null, 실제로 선택 가능한 값은 nullable로 표현 |
| JSON 바인딩 | 필수 필드 누락, 명시적 null, 타입 불일치, 알 수 없는 enum 값 |
| Bean Validation | 공백, 길이, 이메일 형식, 비용 범위, 중첩 객체 제약 |
| 도메인·서비스 | 카탈로그 유효성, 상태별 필수 데이터, 업무 규칙 |
| 영속성 | 필수 컬럼·고유성 등 저장 무결성 |

non-null `String`도 빈 문자열과 공백을 담을 수 있다. Null Safety와 값 검증을 함께 사용한다. Java 플랫폼 타입은 경계에서 null 가능성을 확인해 Kotlin 타입으로 정리하고, `!!` 대신 명시적인 검증과 의미 있는 실패를 사용한다. [Kotlin Null Safety 문서](https://kotlinlang.org/docs/null-safety.html).

## DTO 규칙

- 생성·수정·승인·반려는 행위에 필요한 필드만 받는다. 상태·작성자·처리자·역할처럼 서버가 결정하는 값은 일반 업무 요청의 변경 대상에서 제외한다.
- 필수 입력에는 의미 없는 기본값을 두지 않는다. 기본값은 누락이 유효한 업무 의미를 갖는 경우에 사용한다.
- 입력 DTO의 Bean Validation 어노테이션은 `@field:NotBlank`, `@field:Size`처럼 적용 위치를 명시한다. 실제 JVM 필드·getter·생성자 중 어디에 붙는지 구분한다. [Kotlin 어노테이션 대상 문서](https://kotlinlang.org/docs/annotations.html#annotation-use-site-targets).
- 컨트롤러의 요청 객체에는 `@Valid`를 적용하고, 중첩 객체도 검증이 전파되도록 설정한다. 바인딩 실패와 검증 실패는 각각 일관된 `400` 응답으로 처리한다. [Spring MVC 검증 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html).
- 수정 API는 전체 교체인지 부분 수정인지 명시한다. 부분 수정에서 필드 누락과 명시적 null을 구분해야 한다면 단순한 nullable 프로퍼티만으로 표현하지 않는다.
- DTO에서 엔티티로 전달하기 전에 입력 제약을 충족시킨다. 소유권과 역할은 [인가 정책](../security/authentication-authorization.md), 허용된 상태 변경은 [도메인 규칙](../domain/resource-requests.md)으로 검증한다.

## 검증 기준

HTTP 요청으로 필수 필드 누락·null·공백·길이 초과·잘못된 enum·숫자 타입·비용 범위·중첩 객체 오류를 검증한다. 바인딩 단계에서 실패할 입력과 Bean Validation 단계에서 실패할 입력을 구분한다. 오류 응답에 비밀번호·토큰 등 민감한 원문을 포함하지 않는다.
