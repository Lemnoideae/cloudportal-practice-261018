package com.programmers.be14.cloudportal_practice_261018.domain.request.entity

enum class RequestStatus {
    PENDING,    // 승인 대기
    APPROVED,   // 승인 완료
    REJECTED,   // 반려됨
    CANCELLED   // 신청자 본인에 의해 취소됨
}