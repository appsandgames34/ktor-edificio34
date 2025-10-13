package com.appsandgames34.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtConfig {
    private const val secret = "super_secret_key" // ⚠️ usa variable de entorno en prod
    private const val issuer = "com.appsandgames34"
    private const val audience = "game_audience"

    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(userId: UUID, username: String): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId.toString())
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 3600 * 1000)) // 7 días
            .sign(algorithm)
    }

    fun getVerifier() = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()
}
