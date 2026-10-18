package com.programmers.be14.cloudportal_practice_261018.domain.request.controller

import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.CreateResourceRequestDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.RejectRequestDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.dto.ResourceResponseDto
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.RequestStatus
import com.programmers.be14.cloudportal_practice_261018.domain.request.service.ResourceRequestService
import com.programmers.be14.cloudportal_practice_261018.global.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/requests")
class ResourceRequestController(
    private val resourceRequestService: ResourceRequestService
) {
    // 1. 자원 신청: DEVELOPER만 신청 가능
    @PreAuthorize("hasRole('DEVELOPER')")
    @PostMapping
    fun createRequest(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody dto: CreateResourceRequestDto
    ): ResponseEntity<Map<String, Long>> {

        val id = resourceRequestService.createRequest(principal.id, dto)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("requestId" to id))
    }

    // 2. 내가 신청한 목록 조회: 로그인한 누구나 가능
    @GetMapping("/me")
    fun getMyRequests(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<ResourceResponseDto>> {

        val list = resourceRequestService.getMyRequests(principal.id)
        return ResponseEntity.ok(list)
    }

    // 3. 전체 신청서 목록 조회: 관리자(DEVOPS_ADMIN) 및 감시자(AUDITOR)만 가능
    @PreAuthorize("hasAnyRole('DEVOPS_ADMIN', 'AUDITOR')")
    fun getAllRequests(
        @RequestParam(required = false) status: RequestStatus?
    ): ResponseEntity<List<ResourceResponseDto>> {

        val list = resourceRequestService.getAllRequests(status)
        return ResponseEntity.ok(list)
    }

    // 4. 신청 취소: 소유권 인가 검증 적용 (본인이 작성한 글이어야 취소 가능)
    @PreAuthorize("@requestSecurity.isOwner(#id, principal)")
    @PostMapping
    fun cancelRequest(
        @PathVariable id: Long
    ): ResponseEntity<Unit> {
        resourceRequestService.cancelRequest(id)
        return ResponseEntity.noContent().build()
    }

    // 5. 관리자 승인: DEVOPS_ADMIN만 가능
    @PreAuthorize("hasRole('DEVOPS_ADMIN')")
    @PostMapping("/{id}/approve")
    fun approveRequest(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Unit> {
        resourceRequestService.approveRequest(principal.id, id)
        return ResponseEntity.ok().build()
    }

    // 6. 관리자 반려: DEVOPS_ADMIN만 가능
    @PreAuthorize("hasRole('DEVOPS_ADMIN')")
    @PostMapping("/{id}/reject")
    fun rejectRequest(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody dto: RejectRequestDto
    ): ResponseEntity<Unit> {
        resourceRequestService.rejectRequest(principal.id, id, dto)
        return ResponseEntity.ok().build()
    }
}