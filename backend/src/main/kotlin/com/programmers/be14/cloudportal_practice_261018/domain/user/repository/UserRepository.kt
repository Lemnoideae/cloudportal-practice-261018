package com.programmers.be14.cloudportal_practice_261018.domain.user.repository

import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository: JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}