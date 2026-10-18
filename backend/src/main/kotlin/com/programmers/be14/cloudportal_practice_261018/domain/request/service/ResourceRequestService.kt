package com.programmers.be14.cloudportal_practice_261018.domain.request.service

import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.CreateResourceRequestDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.RejectRequestDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.ResourceResponseDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.RequestStatus
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceRequest
import com.programmers.be14.cloudportal_practice_261018.domain.request.repository.ResourceRequestRepository
import com.programmers.be14.cloudportal_practice_261018.domain.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ResourceRequestService(
    private val resourceRequestRepository: ResourceRequestRepository,
    private val userRepository: UserRepository
) {
    // 1. 자원 신청서 작성(신청자는 본인)
    fun createRequest(userId: Long, dto: CreateResourceRequestDto): Long {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw NoSuchElementException("사용자를 찾을 수 없습니다. (ID: ${userId})")

        val request = ResourceRequest(
            resourceType = dto.resourceType,
            spec = dto.spec,
            reason = dto.reason,
            requester = user
        )

        return resourceRequestRepository.save(request).id!!
    }

    // 2. 본인의 신청 목록 조회
    fun getMyRequests(userId: Long) = resourceRequestRepository
        .findAllByRequesterIdOrderByCreatedAtDesc(userId)
        .map { ResourceResponseDto.from(it) }

    // 3. 전체 목록 조회 (관리자 / 감사자용)
    fun getAllRequests(status: RequestStatus?): List<ResourceResponseDto> {
        val list = if (status != null) {
            resourceRequestRepository.findAllByStatusOrderByCreatedAtDesc(status)
        } else {
            resourceRequestRepository.findAll()
        }
        return list.map { ResourceResponseDto.from(it) }
    }

    // 4. 신청 취소 (작성자 본인만 가능)
    @Transactional
    fun cancelRequest(requestId: Long) {
        val request = resourceRequestRepository.findByIdOrNull(requestId)
            ?: throw NoSuchElementException("신청서를 찾을 수 없습니다. (ID: ${requestId})")

        request.cancel() // 도메인 메서드 호출 (상태 검증 포함)
    }

    // 5. 관리자 승인
    @Transactional
    fun approveRequest(adminId: Long, requestId: Long) {
        val admin = userRepository.findByIdOrNull(adminId)
            ?: throw NoSuchElementException("관리자 정보를 찾을 수 없습니다. (ID: ${adminId})")

        val request = resourceRequestRepository.findByIdOrNull(requestId)
            ?: throw NoSuchElementException("신청서를 찾을 수 없습니다. (ID: ${requestId})")

        request.approve(admin)
    }

    // 6. 관리자 반려
    @Transactional
    fun rejectRequest(adminId: Long, requestId: Long, dto: RejectRequestDto) {
        val admin = userRepository.findByIdOrNull(adminId)
            ?: throw NoSuchElementException("관리자 정보를 찾을 수 없습니다. (ID: ${adminId})")

        val request = resourceRequestRepository.findByIdOrNull(requestId)
            ?: throw NoSuchElementException("신청서를 찾을 수 없습니다. (ID: ${requestId})")

        request.reject(admin, dto.reason)
    }
}