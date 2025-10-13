package com.appsandgames34.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Gestor de conexiones por partida
object GameConnectionManager {
    private val connections = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    fun addConnection(gameId: String, session: WebSocketSession) {
        connections.getOrPut(gameId) { mutableSetOf() }.add(session)
    }

    fun removeConnection(gameId: String, session: WebSocketSession) {
        connections[gameId]?.remove(session)
        if (connections[gameId]?.isEmpty() == true) {
            connections.remove(gameId)
        }
    }

    suspend fun broadcast(gameId: String, message: String, except: WebSocketSession? = null) {
        connections[gameId]?.forEach { session ->
            if (session != except && !session.outgoing.isClosedForSend) {
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    // Ignorar errores de envío
                }
            }
        }
    }

    fun getConnectionCount(gameId: String): Int {
        return connections[gameId]?.size ?: 0
    }
}

fun Route.webSocketRoutes() {
    webSocket("/ws/game/{gameId}") {
        val gameId = call.parameters["gameId"] ?: run {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "gameId no proporcionado"))
            return@webSocket
        }

        // Agregar conexión
        GameConnectionManager.addConnection(gameId, this)

        try {
            // Notificar a otros que un jugador se conectó
            GameConnectionManager.broadcast(gameId, """
                {
                    "type": "player_connected",
                    "gameId": "$gameId",
                    "timestamp": "${System.currentTimeMillis()}"
                }
            """.trimIndent())

            // Escuchar mensajes del cliente
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        // Reenviar el mensaje a todos los demás jugadores
                        GameConnectionManager.broadcast(gameId, text, this)
                    }
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            // Conexión cerrada normalmente
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Remover conexión
            GameConnectionManager.removeConnection(gameId, this)

            // Notificar desconexión
            GameConnectionManager.broadcast(gameId, """
                {
                    "type": "player_disconnected",
                    "gameId": "$gameId",
                    "timestamp": "${System.currentTimeMillis()}"
                }
            """.trimIndent())
        }
    }
}