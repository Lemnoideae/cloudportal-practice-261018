package com.programmers.be14.cloudportal_practice_261018.domain.auth.controller

import com.programmers.be14.cloudportal_practice_261018.domain.auth.dto.LoginRequest
import com.programmers.be14.cloudportal_practice_261018.domain.auth.dto.SignUpRequest
import com.programmers.be14.cloudportal_practice_261018.domain.auth.dto.TokenResponse
import com.programmers.be14.cloudportal_practice_261018.domain.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/signup")
    fun signUp(
        @Valid @RequestBody request: SignUpRequest
    ): ResponseEntity<Map<String, Any>> {

        val userId = authService.signUp(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("userId" to userId))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<TokenResponse> {

        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }
}