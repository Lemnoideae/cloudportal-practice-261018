# Cloud Portal Practice

Java → Kotlin 마이그레이션에 대비해 **Kotlin과 Spring Boot, 특히 Spring Security를 학습하는 프로젝트**입니다.
사내 클라우드 인프라 자원 신청·승인 포털을 구현하며, 업무 규칙을 타입과 보안 정책으로 표현하는 연습을 합니다.

## 구현 목표

- **자원 신청서**: 인스턴스 타입, 사용 목적, 예상 비용을 포함한 신청·조회·수정·취소
- **승인/반려 히스토리**: 처리 결과와 사유 기록·조회
- **자원 스펙 카탈로그**: 신청 가능한 자원 스펙 관리

`DEVELOPER`는 신청·조회, `DEVOPS_ADMIN`은 승인·반려와 카탈로그 관리, `AUDITOR`는 감사용 읽기 전용 역할을 맡습니다.
신청자는 본인이 작성한 대기 중인 신청서만 수정·취소할 수 있으며, 승인된 신청서는 수정할 수 없습니다.

## 핵심 학습 포인트

- **인증과 인가의 분리**: 사용자 식별과 역할·소유권에 따른 접근 제어
- **Kotlin–JPA 매핑**: 생성자, 프록시, 불변성과 영속 상태의 관계
- **Null Safety와 DTO 검증**: null 제약, JSON 바인딩, Bean Validation의 책임 구분
- **상태 머신**: `enum class`와 `sealed class`로 `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED` 상태와 전이 규칙 설계

## 상세 문서

프로젝트 범위와 설계·구현 기준은 [.agents/README.md](.agents/README.md)에서 안내합니다.
