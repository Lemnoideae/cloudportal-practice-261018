package com.programmers.be14.cloudportal_practice_261018.domain.request.repository

import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.RequestStatus
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceRequest
import org.springframework.data.jpa.repository.JpaRepository

interface ResourceRequestRepository: JpaRepository<ResourceRequest, Long> {
    // 특정 사용자가 신청한 목록 조회
    fun findAllByRequesterIdOrderByCreatedAtDesc(requesterId: Long): List<ResourceRequest>

    // 특정 상태(ex: PENDING) 목록 조회
    fun findAllByStatusOrderByCreatedAtDesc(status: RequestStatus): List<ResourceRequest>
}