package com.appsandgames34.routes

import com.appsandgames34.modelos.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Información de conexión por jugador
data class PlayerConnection(
    val session: WebSocketSession,
    val userId: String,
    val playerId: String? = null
)

// Gestor de conexiones por partida
object GameConnectionManager {
    // Map: gameId -> Map de (userId -> PlayerConnection)
    private val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, PlayerConnection>>()

    fun addConnection(gameId: String, userId: String, session: WebSocketSession, playerId: String? = null) {
        val gameConnections = connections.getOrPut(gameId) { ConcurrentHashMap() }
        gameConnections[userId] = PlayerConnection(session, userId, playerId)
        println("✅ Usuario $userId conectado a partida $gameId (Total: ${gameConnections.size} jugadores)")
    }

    fun removeConnection(gameId: String, userId: String) {
        connections[gameId]?.remove(userId)
        if (connections[gameId]?.isEmpty() == true) {
            connections.remove(gameId)
        }
        println("❌ Usuario $userId desconectado de partida $gameId")
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun broadcast(gameId: String, message: String, exceptUserId: String? = null) {
        val gameConnections = connections[gameId] ?: return

        gameConnections.forEach { (userId, playerConnection) ->
            if (userId != exceptUserId && !playerConnection.session.outgoing.isClosedForSend) {
                try {
                    playerConnection.session.send(Frame.Text(message))
                } catch (e: Exception) {
                    println("⚠️ Error enviando mensaje a usuario $userId: ${e.message}")
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun broadcastToAll(gameId: String, message: String) {
        val gameConnections = connections[gameId] ?: return

        gameConnections.forEach { (_, playerConnection) ->
            if (!playerConnection.session.outgoing.isClosedForSend) {
                try {
                    playerConnection.session.send(Frame.Text(message))
                } catch (e: Exception) {
                    println("⚠️ Error enviando mensaje: ${e.message}")
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun sendToPlayer(gameId: String, userId: String, message: String) {
        val playerConnection = connections[gameId]?.get(userId)
        if (playerConnection != null && !playerConnection.session.outgoing.isClosedForSend) {
            try {
                playerConnection.session.send(Frame.Text(message))
            } catch (e: Exception) {
                println("⚠️ Error enviando mensaje directo a usuario $userId: ${e.message}")
            }
        }
    }

    /**
     * Envía actualización completa del estado del juego a todos los jugadores conectados
     */
    suspend fun broadcastGameUpdate(gameId: String) {
        try {
            val gameState = transaction {
                val game = Games
                    .selectAll()
                    .where { Games.id eq UUID.fromString(gameId) }
                    .singleOrNull() ?: return@transaction null

                val players = GamePlayers
                    .innerJoin(Users)
                    .selectAll()
                    .where { GamePlayers.gameId eq UUID.fromString(gameId) }
                    .orderBy(GamePlayers.playerIndex to SortOrder.ASC)
                    .map { playerRow ->
                        mapOf(
                            "playerId" to playerRow[GamePlayers.id].toString(),
                            "userId" to playerRow[GamePlayers.userId].toString(),
                            "username" to playerRow[Users.username],
                            "character" to playerRow[GamePlayers.character],
                            "position" to playerRow[GamePlayers.position],
                            "isReady" to playerRow[GamePlayers.isReady],
                            "connected" to playerRow[GamePlayers.connected],
                            "playerIndex" to playerRow[GamePlayers.playerIndex]
                        )
                    }

                mapOf(
                    "type" to "game_updated",
                    "gameId" to game[Games.id].toString(),
                    "game" to mapOf(
                        "id" to game[Games.id].toString(),
                        "code" to game[Games.code],
                        "maxPlayers" to game[Games.maxPlayers],
                        "status" to game[Games.status],
                        "isStarted" to game[Games.isStarted],
                        "currentTurnIndex" to game[Games.currentTurnIndex],
                        "boardSize" to game[Games.boardSize],
                        "players" to players
                    ),
                    "timestamp" to System.currentTimeMillis()
                )
            }

            if (gameState != null) {
                // Convertir el Map a JSON manualmente para evitar dependencias
                val json = buildJsonString(gameState)
                broadcastToAll(gameId, json)
            }
        } catch (e: Exception) {
            println("❌ Error al enviar actualización del juego: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Construye un JSON string manualmente desde un Map
     */
    private fun buildJsonString(map: Map<String, Any?>): String {
        fun escape(str: String): String = str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        fun toJson(value: Any?): String = when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Map<*, *> -> {
                val entries = value.entries.joinToString(",") { (k, v) ->
                    "\"${escape(k.toString())}\":${toJson(v)}"
                }
                "{$entries}"
            }
            is List<*> -> {
                val items = value.joinToString(",") { toJson(it) }
                "[$items]"
            }
            else -> "\"${escape(value.toString())}\""
        }

        return toJson(map)
    }

    fun getConnectionCount(gameId: String): Int {
        return connections[gameId]?.size ?: 0
    }

    fun isUserConnected(gameId: String, userId: String): Boolean {
        return connections[gameId]?.containsKey(userId) == true
    }

    fun getConnectedUsers(gameId: String): List<String> {
        return connections[gameId]?.keys?.toList() ?: emptyList()
    }

    /**
     * Actualiza el estado de conexión de un jugador en la base de datos
     */
    suspend fun updatePlayerConnectionStatus(gameId: String, userId: String, connected: Boolean) {
        try {
            transaction {
                GamePlayers.update({
                    (GamePlayers.gameId eq UUID.fromString(gameId)) and
                            (GamePlayers.userId eq UUID.fromString(userId))
                }) {
                    it[GamePlayers.connected] = connected
                }
            }
            // Enviar actualización del estado del juego
            broadcastGameUpdate(gameId)
        } catch (e: Exception) {
            println("⚠️ Error actualizando estado de conexión: ${e.message}")
        }
    }
}

fun Route.webSocketRoutes() {
    webSocket("/ws/game/{gameId}") {
        val gameId = call.parameters["gameId"] ?: run {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "gameId no proporcionado"))
            return@webSocket
        }

        val userId = call.request.queryParameters["userId"] ?: run {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "userId no proporcionado"))
            return@webSocket
        }

        val playerId = call.request.queryParameters["playerId"]

        println("🔌 Nueva conexión WebSocket - GameId: $gameId, UserId: $userId")

        // Agregar conexión
        GameConnectionManager.addConnection(gameId, userId, this, playerId)

        // Marcar jugador como conectado en la base de datos
        GameConnectionManager.updatePlayerConnectionStatus(gameId, userId, true)

        try {
            // Enviar mensaje de bienvenida
            send(Frame.Text("""
                {
                    "type": "connection_established",
                    "gameId": "$gameId",
                    "userId": "$userId",
                    "connectedPlayers": ${GameConnectionManager.getConnectionCount(gameId)},
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent()))

            // Notificar a otros jugadores
            GameConnectionManager.broadcast(gameId, """
                {
                    "type": "player_connected",
                    "gameId": "$gameId",
                    "userId": "$userId",
                    "playerId": ${playerId?.let { "\"$it\"" } ?: "null"},
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent(), userId)

            // Escuchar mensajes del cliente
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        println("📨 Mensaje recibido de $userId: ${text.take(100)}")

                        // Reenviar a todos los demás jugadores
                        GameConnectionManager.broadcast(gameId, text, userId)
                    }
                    is Frame.Close -> {
                        println("🔒 Cliente cerró la conexión - UserId: $userId")
                        break
                    }
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            println("🔌 Conexión cerrada normalmente - UserId: $userId")
        } catch (e: Exception) {
            println("❌ Error en WebSocket - UserId: $userId: ${e.message}")
            e.printStackTrace()
        } finally {
            // Remover conexión
            GameConnectionManager.removeConnection(gameId, userId)

            // Marcar jugador como desconectado en la base de datos
            GameConnectionManager.updatePlayerConnectionStatus(gameId, userId, false)

            // Notificar desconexión
            GameConnectionManager.broadcast(gameId, """
                {
                    "type": "player_disconnected",
                    "gameId": "$gameId",
                    "userId": "$userId",
                    "playerId": ${playerId?.let { "\"$it\"" } ?: "null"},
                    "connectedPlayers": ${GameConnectionManager.getConnectionCount(gameId)},
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent())
        }
    }
}

/**
 * IMPORTANTE: Llama a GameConnectionManager.broadcastGameUpdate(gameId)
 * después de cualquier cambio en el estado del juego:
 *
 * En GameRoutes.kt:
 * - Después de crear partida
 * - Después de unirse a partida
 * - Después de salir de partida
 * - Después de marcar ready
 * - Después de tirar dados
 * - Después de iniciar partida
 *
 * Ejemplo:
 * transaction {
 *     // ... modificar estado del juego ...
 * }
 * GameConnectionManager.broadcastGameUpdate(gameId.toString())
 */