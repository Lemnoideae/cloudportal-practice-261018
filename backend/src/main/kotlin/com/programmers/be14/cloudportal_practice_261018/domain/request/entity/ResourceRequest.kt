package com.programmers.be14.cloudportal_practice_261018.domain.request.entity

import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.User
import com.programmers.be14.cloudportal_practice_261018.global.common.BaseTimeEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "resource_requests")
class ResourceRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var resourceType: ResourceType,

    @Column(nullable = false, length = 255)
    var spec: String,

    @Column(nullable = false, length = 1023)
    var reason: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    var requester: User,

    estimatedCost: BigDecimal
): BaseTimeEntity() {

    @field:Version
    @field:Column(nullable = false)
    var version: Long = 0L
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var status: RequestStatus = RequestStatus.PENDING
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "approver_id")
    var approver: User? = null
        protected set

    @field:Column(length = 255)
    var rejectReason: String? = null
        protected set

    @field:Column(nullable = false, precision = 14, scale = 2)
    var estimatedCost: BigDecimal = estimatedCost
        protected set

    // --- 비즈니스 도메인 메서드 ---

    fun approve(admin: User) {
        val next = nextState(
            toState(),
            RequestCommand.Approve(checkNotNull(admin.id))
        )
        this.status = next.status
        this.approver = admin
    }

    fun reject(admin: User, reason: String) {
        val next = nextState(
            toState(),
            RequestCommand.Reject(checkNotNull(admin.id), reason)
        ) as RequestState.Rejected
        this.status = next.status
        this.approver = admin
        this.rejectReason = next.reason
    }

    fun cancel() {
        val next = nextState(
            toState(),
            RequestCommand.Cancel
        )
        this.status = next.status
    }

    private fun toState(): RequestState = when (status) {
        RequestStatus.PENDING -> RequestState.Pending
        RequestStatus.APPROVED -> RequestState.Approved(checkNotNull(approver?.id))
        RequestStatus.REJECTED -> RequestState.Rejected(
            checkNotNull(approver?.id),
            checkNotNull(rejectReason)
        )
        RequestStatus.CANCELLED -> RequestState.Cancelled
        RequestStatus.EXPIRED -> RequestState.Expired
    }
}