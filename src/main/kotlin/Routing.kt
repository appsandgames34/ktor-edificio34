package com.appsandgames34

import com.appsandgames34.routes.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*


fun Application.configureRouting() {
    routing {
        // Rutas públicas
        userRoutes()
        boardRoutes()
        // Rutas protegidas (requieren JWT)
        authenticate("auth-jwt") {
            gameRoutes()
            chatRoutes()
            cardRoutes()
        }

        // WebSocket (puede requerir autenticación adicional si lo deseas)
        webSocketRoutes()
    }
}
