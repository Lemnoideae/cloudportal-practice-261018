# agy-cli 실행 프롬프트

현재 체크아웃 `/home/lemn5584j/IdeaProjects/cloudportal-practice-261018`에서 다음 계획을 실제로 수행해주세요. 사용자는 수정 계획 작성 후 현재 열려 있는 agy-cli에 코드 점검·수정·검증 실행을 위임했습니다.

먼저 루트 AGENTS.md와 .agents/README.md를 읽고, 다음 계획서와 체크리스트를 읽으세요.

- 계획: `.agents/plans/repair-through-cost/README.md`
- 할 일: `.agents/plans/repair-through-cost/TASKS.md`
- 배경 가이드: `.agents/guides/review-fixes/README.md`와 01~03장, 04장의 “예상 비용과 DTO 검증”

현재 워킹트리에는 사용자의 미커밋/일부 스테이징된 코드, 문서, IDE 변경이 있습니다. 현재 diff를 확인하고 이를 보존한 채 필요한 수정만 적용하세요. 해당 수정본이 이 체크아웃에 있으므로 다른 체크아웃으로 옮기지 말고 여기서 진행하세요. 결과는 워킹트리 변경과 체크리스트·검증 결과로 제출해주세요.

범위는 사용자가 직접 수정한 단계의 완성입니다. 대기 신청 수정 API, 승인·반려 이력, 카탈로그, 만료 스케줄러는 후속 학습으로 남겨주세요. 이미 존재하는 enum/sealed 모델과 비용·DTO 변경은 유지하고 연결을 완성하세요.

확인된 문제는 다음과 같습니다. 작업 시작 시 최신 코드를 다시 확인하세요.

1. `./gradlew test --console=plain`이 `ResourceRequestService.kt:33:13 No value passed for parameter 'estimatedCost'`로 compileKotlin에서 실패합니다. 생성 서비스에서 DTO 비용을 엔티티로 전달해야 합니다.
2. 취소 컨트롤러에 `@requestSecurity.isOwner(...)`가 남아 있고 실제 빈 이름과 다릅니다. 인가는 서비스로 모으고 컨트롤러의 해당 참조·중복 검사를 정리하세요.
3. 서비스에서 java.nio.file.AccessDeniedException을 import하고 있습니다. Spring Security의 타입으로 고쳐 실제 소유권 실패가 JSON 403이 되게 하세요.
4. approve만 sealed 전이를 사용하고 reject/cancel은 ensurePending을 사용합니다. 전이를 한 경로로 통일하고 상태·처리자·사유 불변식을 유지하세요.
5. 목적 reason은 DTO 최대 1023자지만 엔티티 컬럼은 기본 길이입니다. 사용자 의도를 살려 1023자 계약으로 맞추고, 반려 사유 길이·비용 정밀도도 입력과 저장을 일치시키세요.
6. 업무 충돌 핸들러를 추가했지만 광범위한 IllegalStateException → 409 핸들러도 남아 있습니다. 내부 오류와 사용자 상태 충돌을 분리하세요.
7. @Version은 추가됐지만 실제 동시 결정 검증이 없습니다. 독립 트랜잭션의 경쟁, 저장·재조회, 인증/인가/DTO 경계를 의미 있는 테스트로 검증하세요.

일반 가입의 DEVELOPER 고정과 JWT placeholder 수정은 이미 적용된 것으로 보이므로 되돌리지 말고 회귀 검증하세요. 구현·테스트 방식은 프로젝트의 실제 Kotlin/Spring 버전에 맞추세요. 실제 UserPrincipal을 사용하는 서비스 메서드 보안 테스트와 실제 토큰을 통과시키는 JWT 필터 테스트를 구분하세요.

필요한 최소 테스트 의존성과 격리된 테스트 설정을 추가할 수 있습니다. 기존 데이터가 있는 DB는 초기화하지 말고 테스트 전용 환경을 사용하세요. MySQL 검증이 불가능하면 가능한 테스트를 실행하고 DB 차이/환경 제약을 명시하세요. 과거 비용 데이터에 임의의 0원을 채우지 말고 정책이 필요한 경우 절차를 기록하세요.

계획만 다시 제안하고 끝내지 말고 실제 코드 수정과 검증까지 진행하세요. 의미 있는 테스트를 추가·실행하고, ./gradlew test 및 git diff --check 결과를 기록하세요. 실행하지 못한 테스트는 통과로 보고하지 마세요. TASKS.md는 검증 근거가 있는 항목만 완료 처리하세요.

완료 시 한국어로 변경 파일, 수정한 동작과 이유, 테스트 명령·결과, 남은 문제·환경 제약을 보고해주세요. 사용자 코드의 최종 변경 사항을 검토할 수 있도록 남겨주세요.
