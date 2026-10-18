package com.programmers.be14.cloudportal_practice_261018.domain.request.dto

import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.RequestStatus
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceRequest
import com.programmers.be14.cloudportal_practice_261018.domain.request.entity.ResourceType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreateResourceRequestDto(
    @field:NotNull(message = "자원 유형은 필수입니다.")
    val resourceType: ResourceType,

    @field:NotBlank(message = "스펙 사양을 입력해주세요. (ex: t3.medium, 100GB")
    @field:Size(max = 255)
    val spec: String,

    @field:NotBlank(message = "신청 사유를 작성해주세요.")
    @field:Size(max = 1023)
    val reason: String,

    @field:NotNull(message = "예상 비용은 필수입니다.")
    @field:DecimalMin("0.00")
    @field:Digits(integer = 12, fraction = 2)
    val estimatedCost: BigDecimal
)

data class RejectRequestDto(
    @field:NotBlank(message = "반려 사유는 필수입니다.")
    @field:Size(max = 255)
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
    val updatedAt: LocalDateTime,
    val estimatedCost: BigDecimal
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
            updatedAt = entity.updatedAt,
            estimatedCost = entity.estimatedCost
        )
    }
}