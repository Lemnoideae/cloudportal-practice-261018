package com.programmers.be14.cloudportal_practice_261018.domain.request.entity

import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.User
import com.programmers.be14.cloudportal_practice_261018.global.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "resource_requests")
class ResourceRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var resourceType: ResourceType,

    @Column(nullable = false)
    var spec: String,

    @Column(nullable = false)
    var reason: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    var requester: User,
): BaseTimeEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RequestStatus = RequestStatus.PENDING
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    var approver: User? = null
        protected set

    @Column
    var rejectReason: String? = null
        protected set

    // --- 비즈니스 도메인 메서드 ---

    fun approve(admin: User) {
        this.status = RequestStatus.APPROVED
        this.approver = admin
    }

    fun reject(admin: User, reason: String) {
        check(this.status == RequestStatus.PENDING)
        this.status = RequestStatus.REJECTED
        this.approver = admin
        this.rejectReason = reason
    }

    fun cancel() {
        check(this.status == RequestStatus.REJECTED) {
            "대기 중인 요청만 취소할 수 있습니다. 현재 상태: ${this.status}"
        }
        this.status = RequestStatus.CANCELLED
    }
}