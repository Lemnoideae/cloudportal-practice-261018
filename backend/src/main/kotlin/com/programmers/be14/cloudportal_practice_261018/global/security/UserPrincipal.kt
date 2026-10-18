package com.programmers.be14.cloudportal_practice_261018.global.security

import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.Role
import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UserPrincipal(
    val id: Long,
    val email: String,
    val role: Role
): UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(
        SimpleGrantedAuthority(role.key))

    override fun getPassword(): String = ""
    override fun getUsername(): String = email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true

    companion object {
        fun from(user: User) = UserPrincipal(
            id = user.id ?: throw IllegalStateException(
                "저장되지 않은 유저의 ID는 null일 수 없습니다."),
            email = user.email,
            role = user.role
        )
    }
}