package com.programmers.be14.cloudportal_practice_261018.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationEntryPoint: AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.apply {
            status = HttpServletResponse.SC_UNAUTHORIZED
            contentType = MediaType.APPLICATION_JSON_VALUE
            characterEncoding = "UTF-8"
            writer.write("""
                {
                    "error": "Unauthorized",
                    "message": "인증 토큰이 유효하지 않거나 누락되었습니다."
                }
            """.trimIndent()
            )
        }
    }
}