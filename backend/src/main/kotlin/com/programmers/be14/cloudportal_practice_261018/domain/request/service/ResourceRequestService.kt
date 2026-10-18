package com.programmers.be14.cloudportal_practice_261018.domain.request.service

import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.CreateResourceRequestDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.RejectRequestDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.ResourceResponseDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.RequestStatus
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceRequest
import com.programmers.be14.cloudportal_practice_261018.domain.request.repository.ResourceRequestRepository
import com.programmers.be14.cloudportal_practice_261018.domain.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ResourceRequestService(
    private val resourceRequestRepository: ResourceRequestRepository,
    private val userRepository: UserRepository
) {
    // 1. 자원 신청서 작성(신청자는 본인)
    @PreAuthorize("hasRole('DEVELOPER') and #userId == principal.id")
    @Transactional
    fun createRequest(userId: Long, dto: CreateResourceRequestDto): Long {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw NoSuchElementException("사용자를 찾을 수 없습니다. (ID: ${userId})")

        val request = ResourceRequest(
            resourceType = dto.resourceType,
            spec = dto.spec,
            reason = dto.reason,
            requester = user,
            estimatedCost = dto.estimatedCost
        )

        return checkNotNull(resourceRequestRepository.save(request).id)
    }

    // 2. 본인의 신청 목록 조회
    @PreAuthorize("isAuthenticated() and #userId == principal.id")
    fun getMyRequests(userId: Long) = resourceRequestRepository
        .findAllByRequesterIdOrderByCreatedAtDesc(userId)
        .map { ResourceResponseDto.from(it) }

    // 3. 전체 목록 조회 (관리자 / 감사자용)
    @PreAuthorize("hasAnyRole('DEVOPS_ADMIN', 'AUDITOR')")
    fun getAllRequests(status: RequestStatus?): List<ResourceResponseDto> {
        val list = if (status == null) {
            resourceRequestRepository.findAll()
        } else {
            resourceRequestRepository.findAllByStatusOrderByCreatedAtDesc(status)
        }
        return list.map { ResourceResponseDto.from(it) }
    }

    // 4. 신청 취소 (작성자 본인만 가능)
    @PreAuthorize("hasRole('DEVELOPER') and #userId == principal.id")
    @Transactional
    fun cancelRequest(userId: Long, requestId: Long) {
        val request = findRequest(requestId)
        if (request.requester.id != userId) {
            throw AccessDeniedException("본인의 신청서만 취소할 수 있습니다.")
        }
        request.cancel() // 도메인 메서드 호출 (상태 검증 포함)
    }

    // 5. 관리자 승인
    @PreAuthorize("hasRole('DEVOPS_ADMIN') and #adminId == principal.id")
    @Transactional
    fun approveRequest(adminId: Long, requestId: Long) {
        val admin = userRepository.findByIdOrNull(adminId)
            ?: throw NoSuchElementException("처리자를 찾을 수 없습니다.")

        findRequest(requestId).approve(admin)
    }

    // 6. 관리자 반려
    @PreAuthorize("hasRole('DEVOPS_ADMIN') and #adminId == principal.id")
    @Transactional
    fun rejectRequest(adminId: Long, requestId: Long, dto: RejectRequestDto) {
        val admin = userRepository.findByIdOrNull(adminId)
            ?: throw NoSuchElementException("처리자를 찾을 수 없습니다.")
        findRequest(requestId).reject(admin, dto.reason)
    }

    private fun findRequest(requestId: Long): ResourceRequest =
        resourceRequestRepository.findByIdOrNull(requestId)
            ?: throw NoSuchElementException("신청서를 찾을 수 없습니다.")
}