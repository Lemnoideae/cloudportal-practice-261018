package com.programmers.be14.cloudportal_practice_261018.domain.request.dto

import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.RequestStatus
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceRequest
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class CreateResourceRequestDto(
    @field:NotNull(message = "자원 유형은 필수입니다.")
    var resourceType: ResourceType,

    @field:NotBlank(message = "스펙 사양을 입력해주세요. (ex: t3.medium, 100GB")
    var spec: String,

    @field:NotBlank(message = "신청 사유를 작성해주세요.")
    val reason: String
)

data class RejectRequestDto(
    @field:NotBlank(message = "반려 사유를 필수입니다.")
    val reason: String
)

data class ResourceResponseDto(
    val id: Long,
    val resourceType: ResourceType,
    val spec: String,
    val reason: String,
    val status: RequestStatus,
    val requesterEmail: String,
    val approverEmail: String?,
    val rejectReason: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(entity: ResourceRequest) = ResourceResponseDto(
            id = entity.id!!,
            resourceType = entity.resourceType,
            spec = entity.spec,
            reason = entity.reason,
            status = entity.status,
            requesterEmail = entity.requester.email,
            approverEmail = entity.approver?.email,
            rejectReason = entity.rejectReason,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}