package com.appsandgames34

import com.appsandgames34.util.JwtConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    install(Authentication) {
    jwt("auth-jwt") {
        verifier(JwtConfig.getVerifier())
        realm = "Game Server"
        validate { credential ->
            if (credential.payload.getClaim("userId").asString() != null)
                JWTPrincipal(credential.payload)
            else null
        }
    }
}
}
