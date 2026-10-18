# Kotlin–JPA 매핑

## 생성과 프록시

Kotlin 클래스와 멤버는 기본적으로 final이다. JPA의 생성자 요구와 Hibernate 프록시 요구를 각각 확인한다. `kotlin("plugin.jpa")`는 JPA 어노테이션 대상의 무인자 생성자를 지원하고, Spring 프록시와 엔티티 프록시에 필요한 open 처리는 컴파일러 플러그인 설정을 확인한다. 두 요구를 같은 기능으로 취급하지 않는다. [Kotlin no-arg 문서](https://kotlinlang.org/docs/no-arg-plugin.html), [all-open 문서](https://kotlinlang.org/docs/all-open-plugin.html).

프로젝트의 실제 플러그인·`allOpen` 설정과 의존 버전은 `backend/build.gradle.kts`에서 확인한다. 문서 예제의 최신 버전을 그대로 복사하지 않고 프로젝트에 적용된 버전의 동작을 검증한다.

## 엔티티 설계

- 엔티티는 일반 `class`, 입력/응답 DTO는 `data class`를 기본으로 한다. 엔티티의 식별성과 변경 가능한 영속 상태를 DTO의 값 동등성과 구분한다.
- `equals`/`hashCode`가 필요하면 생성 전후 식별자와 프록시를 고려한다. 연관관계를 자동으로 포함하는 동등성·문자열 생성은 지연 로딩과 순환 참조를 유발할 수 있다. [Hibernate 엔티티 가이드](https://docs.hibernate.org/orm/7.1/userguide/html_single/#entity).
- 변경 가능한 영속 속성은 필요한 범위에 `var`를 사용하고, 업무 상태는 제한된 setter와 도메인 메서드로 변경한다.
- DB 생성 ID는 저장 전 `Long?`로 표현할 수 있다. 저장 이후 ID가 필요한 경계에서는 검증해 non-null 타입으로 전달한다.
- 필수 업무 값의 Kotlin 타입과 DB `nullable` 제약을 맞춘다. 생성자 요구를 맞추려고 필수 값을 빈 문자열이나 임의의 기본값으로 대체하지 않는다.
- 반려 사유처럼 특정 상태에서만 필요한 값은 저장 구조의 null 허용과 도메인의 상태별 불변식을 함께 정의한다.

## 저장과 조회

- enum은 `EnumType.STRING`으로 저장한다. 이름 변경은 저장 데이터와 API 계약에 영향을 주므로 마이그레이션을 함께 검토한다.
- 금액은 `BigDecimal`과 DB precision/scale을 맞춘다. 통화·기간 정책은 [도메인 문서](../domain/resource-requests.md)에서 정한다.
- 연관관계는 조회 목적에 맞게 로딩하고, 필요한 데이터는 트랜잭션 안에서 DTO로 매핑한다. 엔티티를 API 응답으로 직접 직렬화하지 않는다.
- 목록에서 연관 객체를 읽을 때 N+1을 확인하고, fetch join·EntityGraph·projection 중 조회에 적합한 방식을 선택한다.
- 상태 전이와 이력 저장의 원자성, 동시 변경 제어는 [상태 머신 규칙](../domain/resource-requests.md)을 따른다.

## 검증 기준

생성·저장·flush·영속성 컨텍스트 초기화·재조회 과정을 통해 매핑을 검증한다. 변경한 부분에 따라 지연 로딩, 필수 컬럼 제약, enum 저장, 버전 충돌을 확인한다. 실제 DB에 의존하는 동작은 프로젝트가 사용하는 DB에서도 검증한다.
