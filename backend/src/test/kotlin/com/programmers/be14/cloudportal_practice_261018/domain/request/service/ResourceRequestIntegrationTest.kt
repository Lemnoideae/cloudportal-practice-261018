package com.programmers.be14.cloudportal_practice_261018.domain.request.service

import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.CreateResourceRequestDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.RejectRequestDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.RequestStatus
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceRequest
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceType
import com.programmers.be14.cloudportal_practice_261018.domain.request.exception.RequestStateConflictException
import com.programmers.be14.cloudportal_practice_261018.domain.request.repository.ResourceRequestRepository
import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.Role
import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.User
import com.programmers.be14.cloudportal_practice_261018.domain.user.repository.UserRepository
import com.programmers.be14.cloudportal_practice_261018.global.security.UserPrincipal
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.core.io.FileSystemResource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class ResourceRequestIntegrationTest {

    @Autowired
    private lateinit var resourceRequestService: ResourceRequestService

    @Autowired
    private lateinit var resourceRequestRepository: ResourceRequestRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    private lateinit var developer1: User
    private lateinit var developer2: User
    private lateinit var admin1: User
    private lateinit var admin2: User
    private lateinit var auditor: User

    @BeforeEach
    fun setUp() {
        resourceRequestRepository.deleteAll()
        userRepository.deleteAll()

        developer1 = userRepository.save(
            User(email = "dev1@test.com", passwordHash = "pw", username = "dev1", role = Role.DEVELOPER)
        )
        developer2 = userRepository.save(
            User(email = "dev2@test.com", passwordHash = "pw", username = "dev2", role = Role.DEVELOPER)
        )
        admin1 = userRepository.save(
            User(email = "admin1@test.com", passwordHash = "pw", username = "admin1", role = Role.DEVOPS_ADMIN)
        )
        admin2 = userRepository.save(
            User(email = "admin2@test.com", passwordHash = "pw", username = "admin2", role = Role.DEVOPS_ADMIN)
        )
        auditor = userRepository.save(
            User(email = "auditor@test.com", passwordHash = "pw", username = "auditor", role = Role.AUDITOR)
        )
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(user: User) {
        val principal = UserPrincipal.from(user)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    @Test
    fun `비용, spec 255자, 목적 1023자, 반려 사유 255자 및 version 증가가 DB 저장과 재조회에서 유지된다`() {
        val spec255 = "s".repeat(255)
        val reason1023 = "r".repeat(1023)
        val cost = BigDecimal("123456789012.34")

        authenticateAs(developer1)
        val requestId = resourceRequestService.createRequest(
            checkNotNull(developer1.id),
            CreateResourceRequestDto(
                resourceType = ResourceType.RDS_DATABASE,
                spec = spec255,
                reason = reason1023,
                estimatedCost = cost
            )
        )

        val txTemplate = TransactionTemplate(transactionManager)
        txTemplate.execute {
            entityManager.clear()
            val saved = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
            assertEquals(0L, saved.version)
            assertEquals(spec255, saved.spec)
            assertEquals(reason1023, saved.reason)
            assertEquals(0, cost.compareTo(saved.estimatedCost))
            assertEquals(RequestStatus.PENDING, saved.status)
        }

        val rejectReason255 = "x".repeat(255)
        authenticateAs(admin1)
        resourceRequestService.rejectRequest(
            checkNotNull(admin1.id),
            requestId,
            RejectRequestDto(reason = rejectReason255)
        )

        val responses = resourceRequestService.getAllRequests(RequestStatus.REJECTED)
        assertEquals(1, responses.size)
        val dto = responses.first()
        assertEquals(requestId, dto.id)
        assertEquals(spec255, dto.spec)
        assertEquals(reason1023, dto.reason)
        assertEquals(0, cost.compareTo(dto.estimatedCost))
        assertEquals(rejectReason255, dto.rejectReason)
        assertEquals(admin1.email, dto.approverEmail)

        txTemplate.execute {
            entityManager.clear()
            val updated = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
            assertEquals(1L, updated.version)
            assertEquals(RequestStatus.REJECTED, updated.status)
            assertEquals(rejectReason255, updated.rejectReason)
        }
    }

    @Test
    fun `주입받은 서비스 프록시 직접 호출에서 역할 부족, 타인 userId 위장, 타인 신청 취소가 모두 거부되고 DB는 변경되지 않는다`() {
        authenticateAs(developer1)
        val requestId = resourceRequestService.createRequest(
            checkNotNull(developer1.id),
            CreateResourceRequestDto(
                resourceType = ResourceType.EC2_INSTANCE,
                spec = "t3.small",
                reason = "테스트 서버",
                estimatedCost = BigDecimal("50.00")
            )
        )

        // 1. developer1이 developer2의 userId로 위장하여 생성/조회/취소 호출 -> 거부
        assertThrows<AccessDeniedException> {
            resourceRequestService.createRequest(
                checkNotNull(developer2.id),
                CreateResourceRequestDto(ResourceType.S3_BUCKET, "100GB", "위장 생성", BigDecimal("10.00"))
            )
        }
        assertThrows<AccessDeniedException> {
            resourceRequestService.getMyRequests(checkNotNull(developer2.id))
        }
        assertThrows<AccessDeniedException> {
            resourceRequestService.cancelRequest(checkNotNull(developer2.id), requestId)
        }

        // 2. developer1이 관리자 기능(승인, 반려, 전체 조회) 호출 -> 거부
        assertThrows<AccessDeniedException> {
            resourceRequestService.approveRequest(checkNotNull(developer1.id), requestId)
        }
        assertThrows<AccessDeniedException> {
            resourceRequestService.rejectRequest(checkNotNull(developer1.id), requestId, RejectRequestDto("사유"))
        }
        assertThrows<AccessDeniedException> {
            resourceRequestService.getAllRequests(null)
        }

        // 3. developer2가 developer1의 신청서를 취소 시도 -> Spring AccessDeniedException으로 거부
        authenticateAs(developer2)
        assertThrows<AccessDeniedException> {
            resourceRequestService.cancelRequest(checkNotNull(developer2.id), requestId)
        }

        // 4. auditor가 모든 쓰기 메서드 호출 -> 거부 (전체 조회만 허용)
        authenticateAs(auditor)
        assertEquals(1, resourceRequestService.getAllRequests(null).size)
        assertThrows<AccessDeniedException> {
            resourceRequestService.createRequest(
                checkNotNull(auditor.id),
                CreateResourceRequestDto(ResourceType.S3_BUCKET, "100GB", "감사자 생성", BigDecimal("10.00"))
            )
        }
        assertThrows<AccessDeniedException> {
            resourceRequestService.cancelRequest(checkNotNull(auditor.id), requestId)
        }
        assertThrows<AccessDeniedException> {
            resourceRequestService.approveRequest(checkNotNull(auditor.id), requestId)
        }
        assertThrows<AccessDeniedException> {
            resourceRequestService.rejectRequest(checkNotNull(auditor.id), requestId, RejectRequestDto("사유"))
        }

        // 5. DB 상태가 전혀 변경되지 않았음을 확인
        val unchanged = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
        assertEquals(RequestStatus.PENDING, unchanged.status)
        assertEquals(0L, unchanged.version)
        assertNull(unchanged.approver)
        assertNull(unchanged.rejectReason)
        assertEquals(1L, resourceRequestRepository.count())
    }

    @Test
    fun `독립 트랜잭션 두 개가 같은 version을 읽고 동시에 결정하면 하나만 커밋되고 다른 하나는 낙관적 잠금 예외로 실패한다`() {
        authenticateAs(developer1)
        val requestId = resourceRequestService.createRequest(
            checkNotNull(developer1.id),
            CreateResourceRequestDto(
                resourceType = ResourceType.RDS_DATABASE,
                spec = "3 nodes",
                reason = "클러스터 증설",
                estimatedCost = BigDecimal("500.00")
            )
        )

        val bothLoadedLatch = CountDownLatch(2)
        val proceedLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val txTemplate = TransactionTemplate(transactionManager)

        val approveTask = Callable<Result<Unit>> {
            runCatching {
                txTemplate.executeWithoutResult {
                    val req = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
                    val admin = checkNotNull(userRepository.findByIdOrNull(checkNotNull(admin1.id)))
                    assertEquals(0L, req.version)
                    bothLoadedLatch.countDown()
                    assertTrue(proceedLatch.await(5, TimeUnit.SECONDS))
                    req.approve(admin)
                }
            }
        }

        val rejectTask = Callable<Result<Unit>> {
            runCatching {
                txTemplate.executeWithoutResult {
                    val req = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
                    val admin = checkNotNull(userRepository.findByIdOrNull(checkNotNull(admin2.id)))
                    assertEquals(0L, req.version)
                    bothLoadedLatch.countDown()
                    assertTrue(proceedLatch.await(5, TimeUnit.SECONDS))
                    req.reject(admin, "동시 반려 사유")
                }
            }
        }

        try {
            val future1 = executor.submit(approveTask)
            val future2 = executor.submit(rejectTask)

            assertTrue(bothLoadedLatch.await(5, TimeUnit.SECONDS), "두 트랜잭션이 모두 version 0을 읽어야 합니다.")
            proceedLatch.countDown()

            val results = listOf(future1.get(5, TimeUnit.SECONDS), future2.get(5, TimeUnit.SECONDS))
            val successes = results.filter { it.isSuccess }
            val failures = results.filter { it.isFailure }

            assertEquals(1, successes.size, "정확히 하나의 트랜잭션만 커밋되어야 합니다.")
            assertEquals(1, failures.size, "다른 하나의 트랜잭션은 충돌로 실패해야 합니다.")

            val exception = checkNotNull(failures.first().exceptionOrNull())
            assertTrue(
                exception is ObjectOptimisticLockingFailureException,
                "예상한 ObjectOptimisticLockingFailureException이 발생해야 합니다. 실제: ${exception::class.qualifiedName}"
            )

            txTemplate.execute {
                entityManager.clear()
                val finalEntity = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
                assertEquals(1L, finalEntity.version)
                assertNotNull(finalEntity.approver)
                if (finalEntity.status == RequestStatus.APPROVED) {
                    assertEquals(admin1.id, finalEntity.approver?.id)
                    assertNull(finalEntity.rejectReason)
                } else {
                    assertEquals(RequestStatus.REJECTED, finalEntity.status)
                    assertEquals(admin2.id, finalEntity.approver?.id)
                    assertEquals("동시 반려 사유", finalEntity.rejectReason)
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `main과 test 설정에서 spring_jpa_open_in_view가 비활성화되고 OSIV 인터셉터가 등록되지 않는다`() {
        assertEquals(false, environment.getProperty("spring.jpa.open-in-view", Boolean::class.java))
        assertEquals(false, environment.getProperty("spring.jpa.show-sql", Boolean::class.java))
        assertNull(environment.getProperty("spring.jpa.properties.open-in-view"))
        assertNull(environment.getProperty("spring.jpa.properties.show-sql"))
        assertTrue(applicationContext.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java).isEmpty())

        val mainYaml = YamlPropertiesFactoryBean().apply {
            setResources(FileSystemResource("src/main/resources/application.yaml"))
        }.getObject()
        checkNotNull(mainYaml)
        assertEquals(false, mainYaml["spring.jpa.open-in-view"])
        assertEquals(true, mainYaml["spring.jpa.show-sql"])
        assertNull(mainYaml["spring.jpa.properties.open-in-view"])
        assertNull(mainYaml["spring.jpa.properties.show-sql"])
    }

    @Test
    fun `DB에 EXPIRED 상태로 저장된 신청서는 재조회 후 승인 반려 취소가 모두 409 충돌로 거부되고 상태와 버전이 보존된다`() {
        val txTemplate = TransactionTemplate(transactionManager)

        val requestId = checkNotNull(
            txTemplate.execute {
                val entity = ResourceRequest(
                    requester = developer1,
                    resourceType = ResourceType.RDS_DATABASE,
                    spec = "db.t3.medium",
                    reason = "만료 영속성 검증",
                    estimatedCost = BigDecimal("42000.00")
                )
                val statusField = ResourceRequest::class.java.getDeclaredField("status").apply {
                    isAccessible = true
                }
                statusField.set(entity, RequestStatus.EXPIRED)
                val saved = resourceRequestRepository.saveAndFlush(entity)
                entityManager.clear()
                saved.id
            }
        )

        val initialLoaded = checkNotNull(
            txTemplate.execute {
                entityManager.clear()
                resourceRequestRepository.findByIdOrNull(requestId)
            }
        )
        assertEquals(RequestStatus.EXPIRED, initialLoaded.status)
        assertNull(initialLoaded.approver)
        assertNull(initialLoaded.rejectReason)
        val initialVersion = initialLoaded.version

        authenticateAs(admin1)
        val approveEx = assertThrows<RequestStateConflictException> {
            resourceRequestService.approveRequest(checkNotNull(admin1.id), requestId)
        }
        assertEquals("완료된 신청서는 다시 처리할 수 없습니다.", approveEx.message)

        val rejectEx = assertThrows<RequestStateConflictException> {
            resourceRequestService.rejectRequest(checkNotNull(admin1.id), requestId, RejectRequestDto(reason = "만료건 반려 시도"))
        }
        assertEquals("완료된 신청서는 다시 처리할 수 없습니다.", rejectEx.message)

        authenticateAs(developer1)
        val cancelEx = assertThrows<RequestStateConflictException> {
            resourceRequestService.cancelRequest(checkNotNull(developer1.id), requestId)
        }
        assertEquals("완료된 신청서는 다시 처리할 수 없습니다.", cancelEx.message)

        txTemplate.execute {
            entityManager.flush()
            entityManager.clear()
            val reloaded = checkNotNull(resourceRequestRepository.findByIdOrNull(requestId))
            assertEquals(RequestStatus.EXPIRED, reloaded.status)
            assertNull(reloaded.approver)
            assertNull(reloaded.rejectReason)
            assertEquals(initialVersion, reloaded.version)
            assertEquals(0, reloaded.estimatedCost.compareTo(BigDecimal("42000.00")))
        }
    }
}
