package com.programmers.be14.cloudportal_practice_261018.global.error

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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

    // check() 상태 머신 규칙 위반 ->  409 Conflict
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse("CONFLICT", e.message
            ?: "현재 상태에서는 처리할 수 없습니다."))

    // 리소스 미존재 -> 404 Not Found
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(e: NoSuchElementException
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse("NOT_FOUND", e.message
            ?: "대상을 찾을 수 없습니다."))

}
