package com.programmers.be14.cloudportal_practice_261018.domain.auth.service

import com.programmers.be14.cloudportal_practice_261018.domain.auth.dto.LoginRequest
import com.programmers.be14.cloudportal_practice_261018.domain.auth.dto.SignUpRequest
import com.programmers.be14.cloudportal_practice_261018.domain.auth.dto.TokenResponse
import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.User
import com.programmers.be14.cloudportal_practice_261018.domain.user.repository.UserRepository
import com.programmers.be14.cloudportal_practice_261018.global.security.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Transactional
    fun signUp(request: SignUpRequest): Long {
        require(!userRepository.existsByEmail(request.email)) {
            "이미 가입된 이메일입니다: ${request.email}"
        }

        val user = passwordEncoder.encode(request.password)?.let {
            User(
                email = request.email,
                passwordHash = it,
                username = request.username,
                role = request.role
            )
        }

        return userRepository.save(user)?.id!!
    }

    fun login(request: LoginRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.")

        require(passwordEncoder.matches(
            request.password,
            user.passwordHash)) {
            "이메일 또는 비밀번호가 일치하지 않습니다."
        }

        val token = jwtTokenProvider.createAccessToken(
            userId = user.id!!,
            email = user.email,
            role = user.role
        )

        return TokenResponse(accessToken = token)
    }
}