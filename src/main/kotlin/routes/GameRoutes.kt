package com.appsandgames34.routes

import com.appsandgames34.modelos.GamePlayers
import com.appsandgames34.modelos.Games
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

// --- Data Classes ---

data class CreateGameRequest(val hostUserId: String, val maxPlayers: Int)
data class JoinGameRequest(val userId: String, val gameId: String)

// --- Routing Functions ---

fun Route.gameRoutes() {

    route("/games") {

        post("/create") {
            try {
                val req = call.receive<CreateGameRequest>()

                // 1. Asignar y validar el UUID. Si falla, el catch lo gestiona.
                val userId: UUID = try {
                    UUID.fromString(req.hostUserId)
                } catch (e: IllegalArgumentException) {
                    call.respondText("ID de usuario host inválido", status = HttpStatusCode.BadRequest)
                    return@post // Aborta la función si es inválido
                } catch (e: Exception) {
                    // Manejar ContentTransformationException si el cuerpo es incorrecto
                    call.respondText("Petición inválida", status = HttpStatusCode.BadRequest)
                    return@post
                }

                val newGameId = UUID.randomUUID()
                // Generar un código de 6 caracteres es suficiente para el índice.
                val gameCode = UUID.randomUUID().toString().take(6)

                // 2. Transacción Única: Garantía de atomicidad (Validación + Inserción)
                transaction {

                    // a) VALIDACIÓN: Verificar si el usuario ya está en otra partida activa
                    val userInAnotherGame = GamePlayers
                        .join(Games, JoinType.INNER, additionalConstraint = { GamePlayers.gameId eq Games.id })
                        .select ( (GamePlayers.userId eq userId) and (Games.status neq "FINISHED") )
                        .any()

                    if (userInAnotherGame) {
                        // Usamos throw para forzar el rollback y saltar la inserción
                        throw IllegalStateException("El usuario ya está en otra partida activa")
                    }

                    // b) INSERCIÓN DE LA PARTIDA
                    Games.insert {
                        it[id] = newGameId
                        it[code] = gameCode
                        it[maxPlayers] = req.maxPlayers
                        it[isStarted] = false
                        it[status] = "WAITING"
                        it[createdAt] = LocalDateTime.now()
                        it[updatedAt] = null
                    }

                    // c) INSERCIÓN DEL HOST COMO JUGADOR
                    GamePlayers.insert {
                        it[GamePlayers.id] = UUID.randomUUID()
                        it[GamePlayers.gameId] = newGameId
                        it[GamePlayers.userId] = userId
                        it[GamePlayers.playerIndex] = 0
                        it[GamePlayers.character] = (1..6).random()
                        it[GamePlayers.position] = 1
                        it[GamePlayers.isReady] = true
                        it[GamePlayers.connected] = true
                        it[GamePlayers.createdAt] = LocalDateTime.now()
                    }
                }

                // 3. Respuesta exitosa
                call.respond(mapOf("gameId" to newGameId, "code" to gameCode))

            } catch (e: IllegalStateException) {
                // Maneja el error de validación (Ej: "El usuario ya está en otra partida activa")
                call.respondText(e.message ?: "Error de validación", status = HttpStatusCode.BadRequest)
            } catch (e: ContentTransformationException) {
                // Captura si el body de la petición no es válido
                call.respondText("Formato de petición JSON inválido", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                // Manejo genérico de errores de base de datos o internos
                call.respondText("Error interno al crear la partida: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // ------------------------------------------------------------------------------------------------

        post("/join") {
            try {
                val req = call.receive<JoinGameRequest>()

                // 1. Validación de UUIDs de entrada
                val userId = try { UUID.fromString(req.userId) } catch (e: Exception) {
                    call.respondText("ID de usuario inválido", status = HttpStatusCode.BadRequest)
                    return@post
                }
                val gameId = try { UUID.fromString(req.gameId) } catch (e: Exception) {
                    call.respondText("ID de partida inválido", status = HttpStatusCode.BadRequest)
                    return@post
                }

                // 2. Transacción para Validaciones y Cálculo
                val availableIndex = transaction {
                    val game = Games.select(Games.id eq gameId).singleOrNull()
                    if (game == null) {
                        throw NoSuchElementException("Partida no encontrada")
                    }

                    // a) Verificar si el jugador ya está en otra partida activa
                    val userInAnotherGame = GamePlayers
                        .join(Games, JoinType.INNER, additionalConstraint = { GamePlayers.gameId eq Games.id })
                        .select ( (GamePlayers.userId eq userId) and (Games.status neq "FINISHED") )
                        .any()

                    if (userInAnotherGame) {
                        throw IllegalStateException("El usuario ya está en otra partida activa")
                    }

                    // b) Calcular el índice disponible
                    val usedIndices = GamePlayers.select(GamePlayers.gameId eq gameId)
                        .map { it[GamePlayers.playerIndex] }

                    // Usamos el maxPlayers del juego para el límite
                    val availableIndex = (0 until game[Games.maxPlayers]).firstOrNull { it !in usedIndices }

                    if (availableIndex == null) {
                        throw IllegalStateException("La partida está llena")
                    }

                    availableIndex // Retorna el índice para la siguiente transacción
                }

                // 3. Transacción para Inserción (separada para mejor lectura y enfoque)
                transaction {
                    GamePlayers.insert {
                        it[GamePlayers.id] = UUID.randomUUID()
                        it[GamePlayers.gameId] = gameId  // Calificar con GamePlayers.gameId
                        it[GamePlayers.userId] = userId  // Calificar con GamePlayers.userId
                        it[GamePlayers.playerIndex] = availableIndex
                        it[GamePlayers.character] = (1..6).random()
                        it[GamePlayers.position] = 1
                        it[GamePlayers.isReady] = false
                        it[GamePlayers.connected] = true
                        it[GamePlayers.createdAt] = LocalDateTime.now()
                    }
                }

                // 4. Respuesta exitosa
                call.respondText("Jugador unido a la partida", status = HttpStatusCode.OK)

            } catch (e: NoSuchElementException) {
                call.respondText("Partida no encontrada", status = HttpStatusCode.NotFound)
            } catch (e: IllegalStateException) {
                call.respondText(e.message ?: "Error de validación", status = HttpStatusCode.BadRequest)
            } catch (e: ContentTransformationException) {
                call.respondText("Formato de petición JSON inválido", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondText("Error interno al unirse a la partida: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // ------------------------------------------------------------------------------------------------

        get("/list") {
            try {
                val games = transaction {
                    Games.selectAll().map {
                        mapOf(
                            "id" to it[Games.id].toString(),
                            "code" to it[Games.code],
                            "started" to it[Games.isStarted],
                            "status" to it[Games.status] // Agregar estado para más utilidad
                        )
                    }
                }
                call.respond(games)
            } catch (e: Exception) {
                call.respondText("Error al listar partidas: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}