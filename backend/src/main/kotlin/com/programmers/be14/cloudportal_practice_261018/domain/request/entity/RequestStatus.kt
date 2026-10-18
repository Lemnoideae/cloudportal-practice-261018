package com.programmers.be14.cloudportal_practice_261018.domain.request.entity

import com.programmers.be14.cloudportal_practice_261018.domain.request.exception.RequestStateConflictException

enum class RequestStatus {
    PENDING,    // 승인 대기
    APPROVED,   // 승인 완료
    REJECTED,   // 반려됨
    CANCELLED,  // 신청자 본인에 의해 취소됨
    EXPIRED
}

sealed class RequestState(val status: RequestStatus) {
    data object Pending : RequestState(
        RequestStatus.PENDING)
    data class Approved(val actorId: Long) : RequestState(
        RequestStatus.APPROVED)
    data class Rejected(val actorId: Long, val reason: String) : RequestState(
        RequestStatus.REJECTED)
    data object Cancelled : RequestState(
        RequestStatus.CANCELLED)
    data object Expired : RequestState(
        RequestStatus.EXPIRED)
}

sealed class RequestCommand {
    data class Approve(val actorId: Long) : RequestCommand()
    data class Reject(val actorId: Long, val reason: String) : RequestCommand()
    data object Cancel : RequestCommand()
    data object Expire : RequestCommand()
}

fun nextState(current: RequestState, command: RequestCommand): RequestState =
    when (current) {
        RequestState.Pending -> when (command) {
            is RequestCommand.Approve -> RequestState.Approved(command.actorId)
            is RequestCommand.Reject -> {
                require(command.reason.isNotBlank()) { "반려 사유는 필수입니다." }
                require(command.reason.length <= 255) { "반려 사유는 255자 이하여야 합니다." }
                RequestState.Rejected(command.actorId, command.reason)
            }
            RequestCommand.Cancel -> RequestState.Cancelled
            RequestCommand.Expire -> RequestState.Expired
        }
        is RequestState.Approved, is RequestState.Rejected,
            RequestState.Cancelled, RequestState.Expired ->
                throw RequestStateConflictException(
                        "완료된 신청서는 다시 처리할 수 없습니다.")
    }