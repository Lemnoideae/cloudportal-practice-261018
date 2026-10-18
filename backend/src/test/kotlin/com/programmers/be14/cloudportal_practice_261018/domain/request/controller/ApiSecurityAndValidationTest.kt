package com.programmers.be14.cloudportal_practice_261018.domain.request.controller

import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.RequestStatus
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceRequest
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceType
import com.programmers.be14.cloudportal_practice_261018.domain.request.repository.ResourceRequestRepository
import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.Role
import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.User
import com.programmers.be14.cloudportal_practice_261018.domain.user.repository.UserRepository
import com.programmers.be14.cloudportal_practice_261018.global.error.GlobalExceptionHandler
import com.programmers.be14.cloudportal_practice_261018.global.security.JwtTokenProvider
import org.aopalliance.intercept.MethodInterceptor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.aop.framework.Advised
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityAndValidationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var resourceRequestRepository: ResourceRequestRepository

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    private lateinit var globalExceptionHandler: GlobalExceptionHandler

    @Value($$"${jwt.secret}")
    private lateinit var jwtSecret: String

    private lateinit var dev1: User
    private lateinit var dev2: User
    private lateinit var admin: User
    private lateinit var auditor: User

    private lateinit var dev1Token: String
    private lateinit var dev2Token: String
    private lateinit var adminToken: String
    private lateinit var auditorToken: String

    @BeforeEach
    fun setUp() {
        resourceRequestRepository.deleteAll()
        userRepository.deleteAll()

        dev1 = userRepository.save(User(email = "dev1@portal.io", passwordHash = "pw", username = "dev1", role = Role.DEVELOPER))
        dev2 = userRepository.save(User(email = "dev2@portal.io", passwordHash = "pw", username = "dev2", role = Role.DEVELOPER))
        admin = userRepository.save(User(email = "admin@portal.io", passwordHash = "pw", username = "admin", role = Role.DEVOPS_ADMIN))
        auditor = userRepository.save(User(email = "auditor@portal.io", passwordHash = "pw", username = "auditor", role = Role.AUDITOR))

        dev1Token = jwtTokenProvider.createAccessToken(checkNotNull(dev1.id), dev1.email, dev1.role)
        dev2Token = jwtTokenProvider.createAccessToken(checkNotNull(dev2.id), dev2.email, dev2.role)
        adminToken = jwtTokenProvider.createAccessToken(checkNotNull(admin.id), admin.email, admin.role)
        auditorToken = jwtTokenProvider.createAccessToken(checkNotNull(auditor.id), auditor.email, auditor.role)
    }

    @Test
    fun `일반 회원가입은 DEVELOPER로 저장되며 role 필드를 주입해도 관리자 권한을 얻을 수 없다`() {
        // 1. 정상 가입 요청 -> 201 Created & DEVELOPER 저장
        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"newdev@portal.io","password":"Password123!","username":"newdev"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").isNumber)

        val savedNewDev = checkNotNull(userRepository.findByEmail("newdev@portal.io"))
        assertEquals(Role.DEVELOPER, savedNewDev.role)

        // 2. role: DEVOPS_ADMIN을 포함한 가입 요청 -> 거부(400)되거나 무시되어 DEVELOPER로만 저장됨
        val result = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"attacker@portal.io","password":"Password123!","username":"attacker","role":"DEVOPS_ADMIN"}""")
        ).andReturn()

        val status = result.response.status
        assertTrue(status == 201 || status == 400, "예상 상태 코드는 201(role 무시) 또는 400(알 수 없는 필드 거부)이어야 합니다.")
        val maybeSaved = userRepository.findByEmail("attacker@portal.io")
        if (maybeSaved != null) {
            assertEquals(Role.DEVELOPER, maybeSaved.role, "가입된 사용자의 역할은 절대 DEVOPS_ADMIN이 될 수 없습니다.")
        }
    }

    @Test
    fun `정상 JWT는 인증되고 누락, 만료, 변조된 JWT는 401을 반환한다`() {
        // 정상 JWT -> 200 OK
        mockMvc.perform(
            get("/api/requests/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
        ).andExpect(status().isOk)

        // 1. 토큰 누락 -> 401
        mockMvc.perform(get("/api/requests/me"))
            .andExpect(status().isUnauthorized)

        // 2. 만료된 JWT -> 401
        val expiredProvider = JwtTokenProvider(jwtSecret, -60L)
        val expiredToken = expiredProvider.createAccessToken(checkNotNull(dev1.id), dev1.email, dev1.role)
        mockMvc.perform(
            get("/api/requests/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
        ).andExpect(status().isUnauthorized)

        // 3. 변조된 JWT -> 401
        val tamperedToken = dev1Token.dropLast(4) + "XXXX"
        mockMvc.perform(
            get("/api/requests/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $tamperedToken")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `HTTP 경로 매핑과 역할, 소유권 검증이 JSON 403으로 작동하며 거부 시 DB는 변경되지 않는다`() {
        // 1. DEVELOPER 생성 -> 201
        mockMvc.perform(
            post("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"resourceType":"EC2_INSTANCE","spec":"t3.large","reason":"웹 서버","estimatedCost":250.50}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.requestId").isNumber)

        val created = resourceRequestRepository.findAll().single()
        val requestId = checkNotNull(created.id)
        assertEquals(0, BigDecimal("250.50").compareTo(created.estimatedCost))

        // 2. DEVELOPER가 본인 목록 조회 -> 200 (비용 포함 확인)
        mockMvc.perform(
            get("/api/requests/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(requestId))
            .andExpect(jsonPath("$[0].estimatedCost").value(250.5))

        // 3. AUDITOR가 전체 목록 조회 -> 200, 쓰기(생성/취소/승인/반려) -> 403
        mockMvc.perform(
            get("/api/requests")
                .param("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $auditorToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(requestId))

        mockMvc.perform(
            post("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $auditorToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"resourceType":"S3_BUCKET","spec":"100GB","reason":"감사자 생성","estimatedCost":10.00}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))

        mockMvc.perform(
            post("/api/requests/$requestId/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $auditorToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))

        // 4. DEVELOPER가 승인/반려 시도 -> 403
        mockMvc.perform(
            post("/api/requests/$requestId/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))

        mockMvc.perform(
            post("/api/requests/$requestId/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"개발자 반려 시도"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))

        // 5. 타인(dev2)이 dev1의 신청 취소 시도 -> Spring AccessDeniedException -> 403 JSON FORBIDDEN
        mockMvc.perform(
            post("/api/requests/$requestId/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev2Token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))

        // DB 상태가 PENDING 그대로 유지되었는지 확인
        val afterDenied = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
        assertEquals(RequestStatus.PENDING, afterDenied.status)
        assertNull(afterDenied.approver)

        // 6. 본인(dev1)이 취소 -> 204 No Content & CANCELLED 반영
        mockMvc.perform(
            post("/api/requests/$requestId/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
        )
            .andExpect(status().isNoContent)

        val cancelled = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
        assertEquals(RequestStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `완료된 신청서에 대한 재승인, 반려, 취소는 409 INVALID_TRANSITION을 반환한다`() {
        val req = resourceRequestRepository.save(
            ResourceRequest(
                resourceType = ResourceType.EC2_INSTANCE,
                spec = "t3.micro",
                reason = "테스트",
                requester = dev1,
                estimatedCost = BigDecimal("30.00")
            )
        )
        val requestId = checkNotNull(req.id)

        // 관리자 승인 -> 200 OK
        mockMvc.perform(
            post("/api/requests/$requestId/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
        ).andExpect(status().isOk)

        // 이미 승인된 건에 대해 재승인/반려/취소 -> 모두 409 INVALID_TRANSITION
        mockMvc.perform(
            post("/api/requests/$requestId/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("INVALID_TRANSITION"))

        mockMvc.perform(
            post("/api/requests/$requestId/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"뒤늦은 반려"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("INVALID_TRANSITION"))

        mockMvc.perform(
            post("/api/requests/$requestId/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("INVALID_TRANSITION"))
    }

    @Test
    fun `낙관적 잠금 예외는 실제 HTTP 요청 경계에서 409 CONCURRENT_MODIFICATION으로 변환되고 내부 IllegalStateException은 409 핸들러에서 제외된다`() {
        val req = resourceRequestRepository.save(
            ResourceRequest(
                resourceType = ResourceType.EC2_INSTANCE,
                spec = "t3.micro",
                reason = "동시 요청 경계 테스트",
                requester = dev1,
                estimatedCost = BigDecimal("40.00")
            )
        )
        val requestId = checkNotNull(req.id)

        val bothLoadedLatch = CountDownLatch(2)
        val proceedLatch = CountDownLatch(1)
        val advised = resourceRequestRepository as Advised
        val interceptor = MethodInterceptor { invocation ->
            val result = invocation.proceed()
            if (invocation.method.name == "findById" && invocation.arguments.firstOrNull() == requestId) {
                bothLoadedLatch.countDown()
                proceedLatch.await(5, TimeUnit.SECONDS)
            }
            result
        }

        val executor = Executors.newFixedThreadPool(2)
        advised.addAdvice(0, interceptor)
        try {
            val approveCall = Callable {
                mockMvc.perform(
                    post("/api/requests/$requestId/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                ).andReturn().response
            }
            val rejectCall = Callable {
                mockMvc.perform(
                    post("/api/requests/$requestId/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"reason":"동시 HTTP 반려"}""")
                ).andReturn().response
            }

            val f1 = executor.submit(approveCall)
            val f2 = executor.submit(rejectCall)

            assertTrue(bothLoadedLatch.await(5, TimeUnit.SECONDS), "두 HTTP 요청 모두 version 0 엔티티를 조회해야 합니다.")
            proceedLatch.countDown()

            val responses = listOf(f1.get(5, TimeUnit.SECONDS), f2.get(5, TimeUnit.SECONDS))
            val statusCodes = responses.map { it.status }.sorted()
            assertEquals(listOf(200, 409), statusCodes, "하나는 200 OK, 다른 하나는 409 Conflict여야 합니다.")

            val conflictBody = responses.first { it.status == 409 }.contentAsString
            assertTrue(conflictBody.contains("\"CONCURRENT_MODIFICATION\""), "409 응답 코드는 CONCURRENT_MODIFICATION이어야 합니다: $conflictBody")
        } finally {
            advised.removeAdvice(interceptor)
            executor.shutdownNow()
        }

        val lockResponse = globalExceptionHandler.handleConcurrentDecision(
            ObjectOptimisticLockingFailureException(ResourceRequest::class.java, 1L)
        )
        assertEquals(HttpStatus.CONFLICT, lockResponse.statusCode)
        assertEquals("CONCURRENT_MODIFICATION", lockResponse.body?.error)

        // GlobalExceptionHandler에 IllegalStateException을 처리하는 메서드가 없음을 리플렉션으로도 확인
        val handlerMethods = GlobalExceptionHandler::class.java.declaredMethods
        val handlesIllegalState = handlerMethods.any { method ->
            method.parameterTypes.any { it == IllegalStateException::class.java }
        }
        assertFalse(handlesIllegalState, "내부 오류인 IllegalStateException을 409로 매핑하는 핸들러가 없어야 합니다.")
    }

    @Test
    fun `DTO 비용, 길이, 공백, enum, 필수값 누락 경계 검증이 400을 반환하고 민감한 원문을 노출하지 않는다`() {
        val req = resourceRequestRepository.save(
            ResourceRequest(
                resourceType = ResourceType.EC2_INSTANCE,
                spec = "t3.micro",
                reason = "테스트",
                requester = dev1,
                estimatedCost = BigDecimal("30.00")
            )
        )
        val requestId = checkNotNull(req.id)

        val invalidCreatePayloads = listOf(
            // 비용 누락
            """{"resourceType":"EC2_INSTANCE","spec":"t3.medium","reason":"사유"}""",
            // 비용 null
            """{"resourceType":"EC2_INSTANCE","spec":"t3.medium","reason":"사유","estimatedCost":null}""",
            // 비용 음수
            """{"resourceType":"EC2_INSTANCE","spec":"t3.medium","reason":"사유","estimatedCost":-0.01}""",
            // 비용 정수부 13자리 (12자리 초과)
            """{"resourceType":"EC2_INSTANCE","spec":"t3.medium","reason":"사유","estimatedCost":1000000000000.00}""",
            // 비용 소수부 3자리 (2자리 초과)
            """{"resourceType":"EC2_INSTANCE","spec":"t3.medium","reason":"사유","estimatedCost":100.123}""",
            // spec 공백
            """{"resourceType":"EC2_INSTANCE","spec":"   ","reason":"사유","estimatedCost":10.00}""",
            // spec 256자 초과
            """{"resourceType":"EC2_INSTANCE","spec":"${"s".repeat(256)}","reason":"사유","estimatedCost":10.00}""",
            // reason 공백
            """{"resourceType":"EC2_INSTANCE","spec":"t3.medium","reason":"   ","estimatedCost":10.00}""",
            // reason 1024자 초과
            """{"resourceType":"EC2_INSTANCE","spec":"t3.medium","reason":"${"r".repeat(1024)}","estimatedCost":10.00}""",
            // 잘못된 enum + 민감 원문 포함
            """{"resourceType":"INVALID_ENUM_SensitiveToken999","spec":"t3.medium","reason":"사유","estimatedCost":10.00}"""
        )

        for (payload in invalidCreatePayloads) {
            val response = mockMvc.perform(
                post("/api/requests")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andReturn()
                .response
                .contentAsString

            assertFalse(response.contains("SensitiveToken999"), "오류 응답에 요청 원문이 노출되지 않아야 합니다.")
        }

        // 반려 사유 공백 및 256자 초과 -> 400
        val invalidRejectPayloads = listOf(
            """{"reason":"   "}""",
            """{"reason":"${"x".repeat(256)}"}""",
            """{}"""
        )

        for (payload in invalidRejectPayloads) {
            mockMvc.perform(
                post("/api/requests/$requestId/reject")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        }

        // 유효 최대 경계값 (spec 255자, reason 1023자, cost 정수 12자리 + 소수 2자리) HTTP 생성 성공 -> 201
        mockMvc.perform(
            post("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $dev1Token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"resourceType":"RDS_DATABASE","spec":"${"s".repeat(255)}","reason":"${"r".repeat(1023)}","estimatedCost":999999999999.99}"""
                )
        ).andExpect(status().isCreated)

        // 유효 최대 경계값 (반려 사유 255자) HTTP 반려 성공 -> 200
        mockMvc.perform(
            post("/api/requests/$requestId/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"${"x".repeat(255)}"}""")
        ).andExpect(status().isOk)

        val rejected = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
        assertEquals(RequestStatus.REJECTED, rejected.status)
        assertEquals(255, rejected.rejectReason?.length)
    }

    @Test
    fun `쿼리 파라미터 enum 불일치와 경로 변수 숫자 변환 실패는 보안 필터 체인을 통과한 뒤 공통 INVALID_REQUEST 400을 반환하고 원문이나 내부 타입명을 노출하지 않는다`() {
        // 1. 미인증 상태에서 잘못된 쿼리 파라미터 요청 시 보안 필터 체인에서 먼저 401 반환
        mockMvc.perform(
            get("/api/requests")
                .param("status", "INVALID_ENUM_SensitiveToken999")
        ).andExpect(status().isUnauthorized)

        // 2. 관리자 JWT로 잘못된 status enum 쿼리 파라미터 요청 -> 400 INVALID_REQUEST & 원문/내부 클래스명 미노출
        val enumMismatchBody = mockMvc.perform(
            get("/api/requests")
                .param("status", "INVALID_ENUM_SensitiveToken999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("요청 형식과 필수 입력값을 확인해주세요."))
            .andReturn()
            .response
            .contentAsString

        assertFalse(enumMismatchBody.contains("SensitiveToken999"), "쿼리 파라미터 입력 원문이 노출되지 않아야 합니다.")
        assertFalse(enumMismatchBody.contains("No enum constant"), "내부 예외 메시지(No enum constant)가 노출되지 않아야 합니다.")
        assertFalse(enumMismatchBody.contains("RequestStatus"), "내부 클래스명이 노출되지 않아야 합니다.")
        assertFalse(enumMismatchBody.contains("BAD_REQUEST"), "오류 코드는 BAD_REQUEST가 아닌 INVALID_REQUEST여야 합니다.")

        // 3. 관리자 JWT로 숫자가 아닌 경로 변수(/api/requests/not-a-number/approve) 요청 -> 400 INVALID_REQUEST & 숫자 변환 예외 미노출
        val pathMismatchBody = mockMvc.perform(
            post("/api/requests/not-a-number-SensitivePath999/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("요청 형식과 필수 입력값을 확인해주세요."))
            .andReturn()
            .response
            .contentAsString

        assertFalse(pathMismatchBody.contains("not-a-number"), "경로 변수 입력 원문이 노출되지 않아야 합니다.")
        assertFalse(pathMismatchBody.contains("SensitivePath999"), "경로 변수 민감 문자열이 노출되지 않아야 합니다.")
        assertFalse(pathMismatchBody.contains("For input string"), "숫자 변환 예외 메시지(For input string)가 노출되지 않아야 합니다.")
        assertFalse(pathMismatchBody.contains("NumberFormatException"), "내부 예외 클래스명이 노출되지 않아야 합니다.")
        assertFalse(pathMismatchBody.contains("BAD_REQUEST"), "오류 코드는 BAD_REQUEST가 아닌 INVALID_REQUEST여야 합니다.")
    }
}
