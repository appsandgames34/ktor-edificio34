package com.appsandgames34.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class UserConnection(
    val userId: String,
    val session: WebSocketSession
)

// Gestor de conexiones por partida mejorado
object GameConnectionManager {
    // Mapeo: gameId -> Map<userId, WebSocketSession>
    private val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()

    fun addConnection(gameId: String, userId: String, session: WebSocketSession) {
        connections.getOrPut(gameId) { ConcurrentHashMap() }[userId] = session
        println("✅ Usuario $userId conectado a partida $gameId")
    }

    fun removeConnection(gameId: String, userId: String) {
        connections[gameId]?.remove(userId)
        if (connections[gameId]?.isEmpty() == true) {
            connections.remove(gameId)
        }
        println("❌ Usuario $userId desconectado de partida $gameId")
    }

    suspend fun broadcastToGame(gameId: String, message: String, exceptUserId: String? = null) {
        val gameConnections = connections[gameId] ?: return
        gameConnections.forEach { (userId, session) ->
            if (userId != exceptUserId && !session.outgoing.isClosedForSend) {
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    println("⚠️ Error enviando mensaje a usuario $userId: ${e.message}")
                }
            }
        }
    }

    suspend fun sendToUser(gameId: String, userId: String, message: String) {
        val session = connections[gameId]?.get(userId)
        if (session != null && !session.outgoing.isClosedForSend) {
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                println("⚠️ Error enviando mensaje directo a usuario $userId: ${e.message}")
            }
        }
    }

    fun isUserConnected(gameId: String, userId: String): Boolean {
        return connections[gameId]?.containsKey(userId) == true
    }

    fun getConnectionCount(gameId: String): Int {
        return connections[gameId]?.size ?: 0
    }

    fun getConnectedUsers(gameId: String): List<String> {
        return connections[gameId]?.keys?.toList() ?: emptyList()
    }
}

fun Route.webSocketRoutes() {
    webSocket("/ws/game/{gameId}") {
        val gameId = call.parameters["gameId"] ?: run {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "gameId no proporcionado"))
            return@webSocket
        }

        val userId = call.parameters["userId"] ?: run {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "userId no proporcionado"))
            return@webSocket
        }

        // Agregar conexión
        GameConnectionManager.addConnection(gameId, userId, this)

        try {
            // Notificar a otros que un jugador se conectó
            GameConnectionManager.broadcastToGame(gameId, """
                {
                    "type": "player_connected",
                    "gameId": "$gameId",
                    "userId": "$userId",
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent())

            // Escuchar mensajes del cliente
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        // Reenviar el mensaje a todos los demás jugadores
                        GameConnectionManager.broadcastToGame(gameId, text, userId)
                    }
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            // Conexión cerrada normalmente
            println("🔌 Conexión cerrada: usuario $userId, partida $gameId")
        } catch (e: Exception) {
            println("❌ Error en WebSocket: ${e.message}")
            e.printStackTrace()
        } finally {
            // Remover conexión
            GameConnectionManager.removeConnection(gameId, userId)

            // Notificar desconexión
            GameConnectionManager.broadcastToGame(gameId, """
                {
                    "type": "player_disconnected",
                    "gameId": "$gameId",
                    "userId": "$userId",
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent())
        }
    }

    // WebSocket dedicado para chat
    webSocket("/ws/game/{gameId}/chat") {
        val gameId = call.parameters["gameId"] ?: run {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "gameId no proporcionado"))
            return@webSocket
        }

        val userId = call.parameters["userId"] ?: run {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "userId no proporcionado"))
            return@webSocket
        }

        println("💬 Chat WebSocket: usuario $userId conectado a partida $gameId")

        // Agregar conexión (usando el mismo manager)
        GameConnectionManager.addConnection(gameId, userId, this)

        try {
            // Escuchar mensajes de chat
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        println("💬 Mensaje recibido de $userId: $text")

                        // Parsear mensaje y guardar en DB (opcional)
                        try {
                            // Broadcast del mensaje a todos en la partida
                            GameConnectionManager.broadcastToGame(gameId, text)
                        } catch (e: Exception) {
                            println("❌ Error procesando mensaje de chat: ${e.message}")
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            println("🔌 Chat cerrado: usuario $userId, partida $gameId")
        } catch (e: Exception) {
            println("❌ Error en Chat WebSocket: ${e.message}")
            e.printStackTrace()
        } finally {
            // Remover conexión
            GameConnectionManager.removeConnection(gameId, userId)
        }
    }
}