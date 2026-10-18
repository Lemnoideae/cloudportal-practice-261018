package com.programmers.be14.cloudportal_practice_261018.domain.request.entity

import com.programmers.be14.cloudportal_practice_261018.domain.request.exception.RequestStateConflictException
import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.Role
import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.User
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestStateTransitionTest {

    private fun createUser(id: Long, email: String, role: Role) = User(
        id = id,
        email = email,
        passwordHash = "encoded-pw",
        username = "user-$id",
        role = role
    )

    private fun createPendingRequest(
        requester: User = createUser(1L, "dev@example.com", Role.DEVELOPER)
    ) = ResourceRequest(
        id = 100L,
        resourceType = ResourceType.EC2_INSTANCE,
        spec = "t3.medium",
        reason = "개발 서버 구축",
        requester = requester,
        estimatedCost = BigDecimal("150000.00")
    )

    @Test
    fun `순수 전이 함수 nextState는 PENDING에서 승인, 반려, 취소, 만료로 전이한다`() {
        val approved = nextState(RequestState.Pending, RequestCommand.Approve(10L))
        assertEquals(RequestState.Approved(10L), approved)
        assertEquals(RequestStatus.APPROVED, approved.status)

        val rejected = nextState(RequestState.Pending, RequestCommand.Reject(10L, "예산 초과"))
        assertEquals(RequestState.Rejected(10L, "예산 초과"), rejected)
        assertEquals(RequestStatus.REJECTED, rejected.status)

        val cancelled = nextState(RequestState.Pending, RequestCommand.Cancel)
        assertEquals(RequestState.Cancelled, cancelled)
        assertEquals(RequestStatus.CANCELLED, cancelled.status)

        val expired = nextState(RequestState.Pending, RequestCommand.Expire)
        assertEquals(RequestState.Expired, expired)
        assertEquals(RequestStatus.EXPIRED, expired.status)
    }

    @Test
    fun `순수 전이 함수 nextState는 모든 완료 상태에서 추가 명령을 거부한다`() {
        val terminalStates = listOf(
            RequestState.Approved(10L),
            RequestState.Rejected(10L, "사유"),
            RequestState.Cancelled,
            RequestState.Expired
        )
        val commands = listOf(
            RequestCommand.Approve(20L),
            RequestCommand.Reject(20L, "다른 사유"),
            RequestCommand.Cancel,
            RequestCommand.Expire
        )

        for (state in terminalStates) {
            for (command in commands) {
                assertThrows<RequestStateConflictException> {
                    nextState(state, command)
                }
            }
        }
    }

    @Test
    fun `엔티티 PENDING에서 승인, 반려, 취소가 각각 정상 반영된다`() {
        val admin = createUser(10L, "admin@example.com", Role.DEVOPS_ADMIN)

        val toApprove = createPendingRequest()
        toApprove.approve(admin)
        assertEquals(RequestStatus.APPROVED, toApprove.status)
        assertEquals(admin.id, toApprove.approver?.id)
        assertNull(toApprove.rejectReason)

        val toReject = createPendingRequest()
        toReject.reject(admin, "스펙 과다")
        assertEquals(RequestStatus.REJECTED, toReject.status)
        assertEquals(admin.id, toReject.approver?.id)
        assertEquals("스펙 과다", toReject.rejectReason)

        val toCancel = createPendingRequest()
        toCancel.cancel()
        assertEquals(RequestStatus.CANCELLED, toCancel.status)
        assertNull(toCancel.approver)
        assertNull(toCancel.rejectReason)
    }

    @Test
    fun `승인된 신청서는 재승인, 반려, 취소할 수 없고 기존 처리자가 유지된다`() {
        val admin = createUser(10L, "admin1@example.com", Role.DEVOPS_ADMIN)
        val anotherAdmin = createUser(20L, "admin2@example.com", Role.DEVOPS_ADMIN)
        val request = createPendingRequest()

        request.approve(admin)

        assertThrows<RequestStateConflictException> { request.approve(anotherAdmin) }
        assertThrows<RequestStateConflictException> { request.reject(anotherAdmin, "뒤늦은 반려") }
        assertThrows<RequestStateConflictException> { request.cancel() }

        assertEquals(RequestStatus.APPROVED, request.status)
        assertEquals(admin.id, request.approver?.id)
        assertNull(request.rejectReason)
    }

    @Test
    fun `반려된 신청서는 승인, 재반려, 취소할 수 없고 기존 처리자와 사유가 유지된다`() {
        val admin = createUser(10L, "admin1@example.com", Role.DEVOPS_ADMIN)
        val anotherAdmin = createUser(20L, "admin2@example.com", Role.DEVOPS_ADMIN)
        val request = createPendingRequest()

        request.reject(admin, "초기 반려 사유")

        assertThrows<RequestStateConflictException> { request.approve(anotherAdmin) }
        assertThrows<RequestStateConflictException> { request.reject(anotherAdmin, "변경된 반려 사유") }
        assertThrows<RequestStateConflictException> { request.cancel() }

        assertEquals(RequestStatus.REJECTED, request.status)
        assertEquals(admin.id, request.approver?.id)
        assertEquals("초기 반려 사유", request.rejectReason)
    }

    @Test
    fun `취소된 신청서는 승인, 반려, 재취소할 수 없고 상태와 처리자가 변경되지 않는다`() {
        val admin = createUser(10L, "admin@example.com", Role.DEVOPS_ADMIN)
        val request = createPendingRequest()

        request.cancel()

        assertThrows<RequestStateConflictException> { request.approve(admin) }
        assertThrows<RequestStateConflictException> { request.reject(admin, "취소 후 반려") }
        assertThrows<RequestStateConflictException> { request.cancel() }

        assertEquals(RequestStatus.CANCELLED, request.status)
        assertNull(request.approver)
        assertNull(request.rejectReason)
    }

    @Test
    fun `만료된 신청서 엔티티는 승인, 반려, 취소할 수 없고 기존 상태와 데이터가 유지된다`() {
        val admin = createUser(10L, "admin@example.com", Role.DEVOPS_ADMIN)
        val request = createPendingRequest()

        // 운영용 공개 상태 setter나 만료 API를 추가하지 않고 테스트 준비 단계에서만 EXPIRED 상태를 구성
        val statusField = ResourceRequest::class.java.getDeclaredField("status").apply {
            isAccessible = true
        }
        statusField.set(request, RequestStatus.EXPIRED)

        assertThrows<RequestStateConflictException> { request.approve(admin) }
        assertThrows<RequestStateConflictException> { request.reject(admin, "만료 후 반려") }
        assertThrows<RequestStateConflictException> { request.cancel() }

        assertEquals(RequestStatus.EXPIRED, request.status)
        assertNull(request.approver)
        assertNull(request.rejectReason)
        assertEquals(0L, request.version)
    }

    @Test
    fun `반려 사유가 공백이거나 255자를 초과하면 실패하고 PENDING 상태를 유지한다`() {
        val admin = createUser(10L, "admin@example.com", Role.DEVOPS_ADMIN)
        val request = createPendingRequest()

        assertThrows<IllegalArgumentException> {
            request.reject(admin, "   ")
        }
        assertThrows<IllegalArgumentException> {
            request.reject(admin, "a".repeat(256))
        }

        assertEquals(RequestStatus.PENDING, request.status)
        assertNull(request.approver)
        assertNull(request.rejectReason)
    }
}
