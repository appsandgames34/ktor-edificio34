package com.appsandgames34.routes

import com.appsandgames34.modelos.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
data class CreateGameRequest(val maxPlayers: Int, val isPublic: Boolean = true)
data class JoinByCodeRequest(val code: String)
data class PlayCardRequest(val cardId: Int, val targetPlayerId: String?)
data class CounterCardRequest(val turnPlayerId: String, val cardId: Int)
data class RollDiceRequest(val gameId: String)
data class ReadyRequest(val gameId: String)

fun Route.gameRoutes() {
    route("/games") {

        // CREAR PARTIDA PRIVADA
        post("/create") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val req = call.receive<CreateGameRequest>()

                if (req.maxPlayers !in 2..6) {
                    call.respondText("El número de jugadores debe estar entre 2 y 6", status = HttpStatusCode.BadRequest)
                    return@post
                }

                val newGameId = UUID.randomUUID()
                val gameCode = UUID.randomUUID().toString().take(6).uppercase()

                transaction {
                    // Verificar si el usuario ya está en otra partida activa
                    val userInAnotherGame = GamePlayers
                        .join(Games, JoinType.INNER, additionalConstraint = { GamePlayers.gameId eq Games.id })
                        .select((GamePlayers.userId eq userId) and (Games.status neq "FINISHED"))
                        .any()

                    if (userInAnotherGame) {
                        throw IllegalStateException("Ya estás en otra partida activa")
                    }

                    // Crear partida
                    Games.insert {
                        it[Games.id] = newGameId
                        it[Games.code] = gameCode
                        it[Games.maxPlayers] = req.maxPlayers
                        it[Games.isStarted] = false
                        it[Games.status] = "WAITING"
                        it[Games.createdAt] = LocalDateTime.now()
                        it[Games.updatedAt] = null
                    }

                    // Añadir host como jugador
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

                    // Crear mazo de cartas para la partida
                    val deck = (1..20).flatMap { cardId -> List(5) { cardId } }.shuffled()
                    GameDecks.insert {
                        it[GameDecks.id] = UUID.randomUUID()
                        it[GameDecks.gameId] = newGameId
                        it[GameDecks.deckCards] = deck.joinToString(",")
                        it[GameDecks.discard] = ""
                        it[GameDecks.createdAt] = LocalDateTime.now()
                    }
                }

                call.respond(mapOf("gameId" to newGameId, "code" to gameCode))

            } catch (e: IllegalStateException) {
                call.respondText(e.message ?: "Error de validación", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondText("Error al crear partida: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // BUSCAR PARTIDA PÚBLICA O CREAR UNA
        post("/find-or-create") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())

                val result = transaction {
                    // Verificar si el usuario ya está en una partida activa
                    val userInAnotherGame = GamePlayers
                        .join(Games, JoinType.INNER, additionalConstraint = { GamePlayers.gameId eq Games.id })
                        .select((GamePlayers.userId eq userId) and (Games.status neq "FINISHED"))
                        .any()

                    if (userInAnotherGame) {
                        throw IllegalStateException("Ya estás en otra partida activa")
                    }

                    // Buscar partida pública con espacio disponible
                    val availableGame = Games
                        .leftJoin(GamePlayers)
                        .select(Games.status eq "WAITING")
                        .groupBy(Games.id, Games.code, Games.maxPlayers)
                        .having { GamePlayers.id.count() less Games.maxPlayers }
                        .map { it[Games.id] to it[Games.code] }
                        .firstOrNull()

                    if (availableGame != null) {
                        // Unirse a partida existente
                        val (gameId, gameCode) = availableGame

                        val usedIndices = GamePlayers.select(GamePlayers.gameId eq gameId)
                            .map { it[GamePlayers.playerIndex] }

                        val usedCharacters = GamePlayers.select(GamePlayers.gameId eq gameId)
                            .map { it[GamePlayers.character] }

                        val availableIndex = (0..5).firstOrNull { it !in usedIndices }
                            ?: throw IllegalStateException("No hay índices disponibles")

                        val availableCharacter = (1..6).firstOrNull { it !in usedCharacters }
                            ?: throw IllegalStateException("No hay personajes disponibles")

                        GamePlayers.insert {
                            it[GamePlayers.id] = UUID.randomUUID()
                            it[GamePlayers.gameId] = gameId
                            it[GamePlayers.userId] = userId
                            it[GamePlayers.playerIndex] = availableIndex
                            it[GamePlayers.character] = availableCharacter
                            it[GamePlayers.position] = 1
                            it[GamePlayers.isReady] = false
                            it[GamePlayers.connected] = true
                            it[GamePlayers.createdAt] = LocalDateTime.now()
                        }

                        mapOf("gameId" to gameId.toString(), "code" to gameCode, "created" to false)
                    } else {
                        // Crear nueva partida pública
                        val newGameId = UUID.randomUUID()
                        val gameCode = UUID.randomUUID().toString().take(6).uppercase()

                        Games.insert {
                            it[Games.id] = newGameId
                            it[Games.code] = gameCode
                            it[Games.maxPlayers] = 6 // Partidas públicas son de 6 jugadores por defecto
                            it[Games.isStarted] = false
                            it[Games.status] = "WAITING"
                            it[Games.createdAt] = LocalDateTime.now()
                            it[Games.updatedAt] = null
                        }

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

                        // Crear mazo
                        val deck = (1..20).flatMap { cardId -> List(5) { cardId } }.shuffled()
                        GameDecks.insert {
                            it[GameDecks.id] = UUID.randomUUID()
                            it[GameDecks.gameId] = newGameId
                            it[GameDecks.deckCards] = deck.joinToString(",")
                            it[GameDecks.discard] = ""
                            it[GameDecks.createdAt] = LocalDateTime.now()
                        }

                        mapOf("gameId" to newGameId.toString(), "code" to gameCode, "created" to true)
                    }
                }

                call.respond(result)

            } catch (e: IllegalStateException) {
                call.respondText(e.message ?: "Error de validación", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // UNIRSE POR CÓDIGO
        post("/join") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val req = call.receive<JoinByCodeRequest>()

                transaction {
                    // Verificar si el usuario ya está en otra partida activa
                    val userInAnotherGame = GamePlayers
                        .join(Games, JoinType.INNER, additionalConstraint = { GamePlayers.gameId eq Games.id })
                        .select((GamePlayers.userId eq userId) and (Games.status neq "FINISHED"))
                        .any()

                    if (userInAnotherGame) {
                        throw IllegalStateException("Ya estás en otra partida activa")
                    }

                    val game = Games.select(Games.code eq req.code.uppercase()).singleOrNull()
                        ?: throw NoSuchElementException("Partida no encontrada")

                    val gameId = game[Games.id]

                    if (game[Games.status] != "WAITING") {
                        throw IllegalStateException("La partida ya ha comenzado")
                    }

                    val playerCount = GamePlayers.select(GamePlayers.gameId eq gameId).count()
                    if (playerCount >= game[Games.maxPlayers]) {
                        throw IllegalStateException("La partida está llena")
                    }

                    val usedIndices = GamePlayers.select(GamePlayers.gameId eq gameId)
                        .map { it[GamePlayers.playerIndex] }

                    val usedCharacters = GamePlayers.select(GamePlayers.gameId eq gameId)
                        .map { it[GamePlayers.character] }

                    val availableIndex = (0 until game[Games.maxPlayers]).firstOrNull { it !in usedIndices }
                        ?: throw IllegalStateException("No hay índices disponibles")

                    val availableCharacter = (1..6).firstOrNull { it !in usedCharacters }
                        ?: throw IllegalStateException("No hay personajes disponibles")

                    GamePlayers.insert {
                        it[GamePlayers.id] = UUID.randomUUID()
                        it[GamePlayers.gameId] = gameId
                        it[GamePlayers.userId] = userId
                        it[GamePlayers.playerIndex] = availableIndex
                        it[GamePlayers.character] = availableCharacter
                        it[GamePlayers.position] = 1
                        it[GamePlayers.isReady] = false
                        it[GamePlayers.connected] = true
                        it[GamePlayers.createdAt] = LocalDateTime.now()
                    }
                }

                call.respondText("Te has unido a la partida", status = HttpStatusCode.OK)

            } catch (e: NoSuchElementException) {
                call.respondText(e.message ?: "Partida no encontrada", status = HttpStatusCode.NotFound)
            } catch (e: IllegalStateException) {
                call.respondText(e.message ?: "Error de validación", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // MARCAR JUGADOR COMO LISTO
        post("/ready") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val req = call.receive<ReadyRequest>()
                val gameId = UUID.fromString(req.gameId)

                transaction {
                    GamePlayers.update({
                        (GamePlayers.gameId eq gameId) and (GamePlayers.userId eq userId)
                    }) {
                        it[isReady] = true
                    }

                    // Verificar si todos están listos para iniciar
                    val players = GamePlayers.select(GamePlayers.gameId eq gameId).toList()
                    val allReady = players.all { it[GamePlayers.isReady] }
                    val game = Games.select(Games.id eq gameId).single()
                    val minPlayers = 2

                    if (allReady && players.count() >= minPlayers) {
                        // Iniciar partida
                        Games.update({ Games.id eq gameId }) {
                            it[isStarted] = true
                            it[status] = "IN_PROGRESS"
                            it[currentTurnIndex] = 0
                            it[updatedAt] = LocalDateTime.now()
                        }

                        // Repartir 3 cartas a cada jugador
                        val deck = GameDecks.select(GameDecks.gameId eq gameId)
                            .single()[GameDecks.deckCards]
                            .split(",")
                            .map { it.toInt() }
                            .toMutableList()

                        players.forEach { player ->
                            val hand = mutableListOf<Int>()
                            repeat(3) {
                                if (deck.isNotEmpty()) {
                                    hand.add(deck.removeFirst())
                                }
                            }

                            PlayerHands.insert {
                                it[PlayerHands.id] = UUID.randomUUID()
                                it[PlayerHands.gameId] = gameId
                                it[PlayerHands.playerId] = player[GamePlayers.id]
                                it[PlayerHands.cards] = hand.joinToString(",")
                            }
                        }

                        // Actualizar mazo
                        GameDecks.update({ GameDecks.gameId eq gameId }) {
                            it[deckCards] = deck.joinToString(",")
                        }
                    }
                }

                call.respondText("Listo para jugar", status = HttpStatusCode.OK)

            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER ESTADO DE LA PARTIDA
        get("/{gameId}") {
            try {
                val gameId = UUID.fromString(call.parameters["gameId"])

                val gameState = transaction {
                    val game = Games.select(Games.id eq gameId).singleOrNull()
                        ?: throw NoSuchElementException("Partida no encontrada")

                    val players = GamePlayers
                        .join(Users, JoinType.INNER, additionalConstraint = { GamePlayers.userId eq Users.id })
                        .select(GamePlayers.gameId eq gameId)
                        .orderBy(GamePlayers.playerIndex to SortOrder.ASC)
                        .map {
                            mapOf(
                                "playerId" to it[GamePlayers.id].toString(),
                                "userId" to it[GamePlayers.userId].toString(),
                                "username" to it[Users.username],
                                "character" to it[GamePlayers.character],
                                "position" to it[GamePlayers.position],
                                "isReady" to it[GamePlayers.isReady],
                                "connected" to it[GamePlayers.connected],
                                "playerIndex" to it[GamePlayers.playerIndex]
                            )
                        }

                    mapOf(
                        "id" to game[Games.id].toString(),
                        "code" to game[Games.code],
                        "maxPlayers" to game[Games.maxPlayers],
                        "status" to game[Games.status],
                        "isStarted" to game[Games.isStarted],
                        "currentTurnIndex" to game[Games.currentTurnIndex],
                        "boardSize" to game[Games.boardSize],
                        "players" to players
                    )
                }

                call.respond(gameState)

            } catch (e: NoSuchElementException) {
                call.respondText("Partida no encontrada", status = HttpStatusCode.NotFound)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // TIRAR DADOS
        post("/roll-dice") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val req = call.receive<RollDiceRequest>()
                val gameId = UUID.fromString(req.gameId)

                val result = transaction {
                    val game = Games.select(Games.id eq gameId).singleOrNull()
                        ?: throw NoSuchElementException("Partida no encontrada")

                    if (game[Games.status] != "IN_PROGRESS") {
                        throw IllegalStateException("La partida no está en progreso")
                    }

                    val player = GamePlayers.select(
                        (GamePlayers.gameId eq gameId) and (GamePlayers.userId eq userId)
                    ).singleOrNull() ?: throw IllegalStateException("No estás en esta partida")

                    if (player[GamePlayers.playerIndex] != game[Games.currentTurnIndex]) {
                        throw IllegalStateException("No es tu turno")
                    }

                    val dice1 = (1..6).random()
                    val dice2 = (1..6).random()
                    val total = dice1 + dice2
                    val newPosition = (player[GamePlayers.position] + total).coerceAtMost(112)

                    GamePlayers.update({ GamePlayers.id eq player[GamePlayers.id] }) {
                        it[position] = newPosition
                    }

                    // Pasar turno
                    val playerCount = GamePlayers.select(GamePlayers.gameId eq gameId).count()
                    val nextTurn = (game[Games.currentTurnIndex] + 1) % playerCount.toInt()

                    Games.update({ Games.id eq gameId }) {
                        it[currentTurnIndex] = nextTurn
                        it[updatedAt] = LocalDateTime.now()
                    }

                    mapOf(
                        "dice1" to dice1,
                        "dice2" to dice2,
                        "total" to total,
                        "newPosition" to newPosition
                    )
                }

                call.respond(result)

            } catch (e: IllegalStateException) {
                call.respondText(e.message ?: "Error", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER MANO DEL JUGADOR
        get("/{gameId}/hand") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val gameId = UUID.fromString(call.parameters["gameId"])

                val hand = transaction {
                    val player = GamePlayers.select(
                        (GamePlayers.gameId eq gameId) and (GamePlayers.userId eq userId)
                    ).singleOrNull() ?: throw IllegalStateException("No estás en esta partida")

                    val playerHand = PlayerHands.select(
                        (PlayerHands.gameId eq gameId) and (PlayerHands.playerId eq player[GamePlayers.id])
                    ).singleOrNull()

                    val cards = playerHand?.get(PlayerHands.cards)
                        ?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.map { it.toInt() } ?: emptyList()

                    mapOf("cards" to cards)
                }

                call.respond(hand)

            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // LISTAR PARTIDAS DISPONIBLES
        get("/available") {
            try {
                val games = transaction {
                    Games
                        .leftJoin(GamePlayers)
                        .select(Games.status eq "WAITING")
                        .groupBy(Games.id, Games.code, Games.maxPlayers, Games.createdAt)
                        .having { GamePlayers.id.count() less Games.maxPlayers }
                        .map {
                            mapOf(
                                "id" to it[Games.id].toString(),
                                "code" to it[Games.code],
                                "maxPlayers" to it[Games.maxPlayers],
                                "currentPlayers" to (it[GamePlayers.id.count()] ?: 0)
                            )
                        }
                }
                call.respond(games)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}