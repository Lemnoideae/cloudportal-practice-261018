package com.programmers.be14.cloudportal_practice_261018.global.error

import com.programmers.be14.cloudportal_practice_261018.domain.request.exception.RequestStateConflictException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

data class ErrorResponse(
    val error: String,
    val message: String
)

@RestControllerAdvice
class GlobalExceptionHandler {

    // 인가 실패 (권한 없음) -> 403 Forbidden
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ErrorResponse("FORBIDDEN",
            "해당 리소스에 접근할 권한이 없습니다."))

    // DTO Validation 실패 -> 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse("INVALID_REQUEST", e.bindingResult
            .fieldErrors.firstOrNull()?.defaultMessage
            ?: "잘못된 입력값입니다."))

    // required() 인자 검증 실패 -> 400 Bad Request
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse("BAD_REQUEST", e.message
            ?: "현재 상태에서는 처리할 수 없습니다."))

    // 리소스 미존재 -> 404 Not Found
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(e: NoSuchElementException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse("NOT_FOUND", e.message
            ?: "대상을 찾을 수 없습니다."))

    // 업무 상태 충돌 -> 409 Conflict
    @ExceptionHandler(RequestStateConflictException::class)
    fun handleRequestStateConflictException(e: RequestStateConflictException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse("INVALID_TRANSITION", e.message
            ?: "현재 상태에서는 처리할 수 없습니다."))

    // 잠금 충돌 -> 409 Conflict
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleConcurrentDecision(e: ObjectOptimisticLockingFailureException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse("CONCURRENT_MODIFICATION",
            "다른 처리가 먼저 반영되었습니다. 다시 조회해주세요."))

    // 필수 필드 누락, null, 잘못된 enum -> 400 Bad Request
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableRequest(e: HttpMessageNotReadableException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.badRequest()
        .body(ErrorResponse("INVALID_REQUEST",
            "요청 형식과 필수 입력값을 확인해주세요."))

    // 쿼리 파라미터·경로 변수 타입/enum 바인딩 실패 -> 400 Bad Request
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(e: MethodArgumentTypeMismatchException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.badRequest()
        .body(ErrorResponse("INVALID_REQUEST",
            "요청 형식과 필수 입력값을 확인해주세요."))
}
