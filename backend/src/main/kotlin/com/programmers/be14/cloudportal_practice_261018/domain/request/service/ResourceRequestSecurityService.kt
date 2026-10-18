package com.programmers.be14.cloudportal_practice_261018.domain.request.service

import com.programmers.be14.cloudportal_practice_261018.domain.request.repository.ResourceRequestRepository
import com.programmers.be14.cloudportal_practice_261018.global.security.UserPrincipal
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ResourceRequestSecurityService(
    private val resourceRequestRepository: ResourceRequestRepository
) {
    // 현재 로그인한 사용자가 해당 신청서의 작성자인지 확인.
    fun isOwner(requestId: Long, principal: UserPrincipal): Boolean {
        val request = resourceRequestRepository
            .findByIdOrNull(requestId)
            ?: return false
        return request.requester.id == principal.id
    }
}