package com.programmers.be14.cloudportal_practice_261018.global.security

import com.programmers.be14.cloudportal_practice_261018.domain.user.entity.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value($$"${jwt.secret}")
    secret: String,

    @Value($$"${jwt.access-token-expiration-seconds")
    private val accessTokenExpirationSeconds: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(
        Decoders.BASE64.decode(secret))

    fun createAccessToken(userId: Long, email: String, role: Role): String {
        val now = Date()
        val validity = Date(now.time + accessTokenExpirationSeconds * 1000)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role.name)
            .issuedAt(now)
            .expiration(validity)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun getUserPrincipal(token: String): UserPrincipal {
        val claims = getClaims(token)
        val userId = claims.subject.toLong()
        val email = claims.get("email", String::class.java)
        val role = Role.valueOf(claims.get("role", String::class.java))

        return UserPrincipal(userId, email, role)
    }

    private fun getClaims(token: String): Claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}