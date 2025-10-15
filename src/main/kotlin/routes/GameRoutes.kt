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
data class CounterCardRequest(val turnPlayerId: String, val cardId: Int)
data class RollDiceRequest(val gameId: String)
data class ReadyRequest(val gameId: String)

// Función auxiliar para iniciar partida automáticamente cuando se llena
private fun startGameIfFull(gameId: UUID) {
    val players = GamePlayers
        .selectAll()
        .where { GamePlayers.gameId eq gameId }
        .toList()

    val game = Games
        .selectAll()
        .where { Games.id eq gameId }
        .singleOrNull() ?: return

    val maxPlayers = game[Games.maxPlayers]
    val currentPlayers = players.count()

    // Si la partida está llena, iniciarla automáticamente
    if (currentPlayers >= maxPlayers && game[Games.status] == "WAITING") {
        println("🎮 Partida llena! Iniciando automáticamente... (${currentPlayers}/${maxPlayers})")

        // Marcar a todos como listos
        GamePlayers.update({ GamePlayers.gameId eq gameId }) {
            it[GamePlayers.isReady] = true
        }

        // Iniciar partida
        Games.update({ Games.id eq gameId }) {
            it[Games.isStarted] = true
            it[Games.status] = "IN_PROGRESS"
            it[Games.currentTurnIndex] = 0
            it[Games.updatedAt] = LocalDateTime.now()
        }

        // Repartir 3 cartas a cada jugador
        val deck = GameDecks
            .selectAll()
            .where { GameDecks.gameId eq gameId }
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
            it[GameDecks.deckCards] = deck.joinToString(",")
        }

        println("✅ Partida iniciada automáticamente!")
    }
}

fun Route.gameRoutes() {
    route("/games") {

        // CREAR PARTIDA PRIVADA
        post("/create") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val req = call.receive<CreateGameRequest>()

                if (req.maxPlayers !in 2..6) {
                    call.respondText(
                        "El número de jugadores debe estar entre 2 y 6",
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }

                val newGameId = UUID.randomUUID()
                val gameCode = UUID.randomUUID().toString().take(6).uppercase()

                transaction {
                    // Verificar si el usuario ya está en otra partida activa
                    val userInAnotherGame = GamePlayers
                        .innerJoin(Games)
                        .selectAll()
                        .where { (GamePlayers.userId eq userId) and (Games.status neq "FINISHED") }
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

                    // Crear mazo de cartas para la partida (21 tipos x 5 copias = 105 cartas)
                    val deck = (1..21).flatMap { cardId -> List(5) { cardId } }.shuffled()
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
                println("❌ Error en /games/create: ${e.message}")
                e.printStackTrace()
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
                        .innerJoin(Games)
                        .selectAll()
                        .where { (GamePlayers.userId eq userId) and (Games.status neq "FINISHED") }
                        .any()

                    if (userInAnotherGame) {
                        throw IllegalStateException("Ya estás en otra partida activa")
                    }

                    // Buscar partida pública con espacio disponible
                    val waitingGames = Games
                        .selectAll()
                        .where { Games.status eq "WAITING" }
                        .toList()

                    val availableGame = waitingGames.firstOrNull { game ->
                        val gameId = game[Games.id]
                        val maxPlayers = game[Games.maxPlayers]
                        val currentPlayers = GamePlayers
                            .selectAll()
                            .where { GamePlayers.gameId eq gameId }
                            .count()
                        currentPlayers < maxPlayers
                    }?.let { it[Games.id] to it[Games.code] }

                    if (availableGame != null) {
                        // Unirse a partida existente
                        val (gameId, gameCode) = availableGame

                        val usedIndices = GamePlayers
                            .selectAll()
                            .where { GamePlayers.gameId eq gameId }
                            .map { it[GamePlayers.playerIndex] }

                        val usedCharacters = GamePlayers
                            .selectAll()
                            .where { GamePlayers.gameId eq gameId }
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

                        // Verificar si la partida se llenó e iniciarla automáticamente
                        startGameIfFull(gameId)

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
                        val deck = (1..21).flatMap { cardId -> List(5) { cardId } }.shuffled()
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
                println("❌ Error en /games/find-or-create: ${e.message}")
                e.printStackTrace()
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // UNIRSE POR CÓDIGO
        post("c/join") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val req = call.receive<JoinByCodeRequest>()

                transaction {
                    // Verificar si el usuario ya está en otra partida activa
                    val userInAnotherGame = GamePlayers
                        .innerJoin(Games)
                        .selectAll()
                        .where { (GamePlayers.userId eq userId) and (Games.status neq "FINISHED") }
                        .any()

                    if (userInAnotherGame) {
                        throw IllegalStateException("Ya estás en otra partida activa")
                    }

                    val game = Games
                        .selectAll()
                        .where { Games.code eq req.code.uppercase() }
                        .singleOrNull()
                        ?: throw NoSuchElementException("Partida no encontrada")

                    val gameId = game[Games.id]

                    if (game[Games.status] != "WAITING") {
                        throw IllegalStateException("La partida ya ha comenzado")
                    }

                    val playerCount = GamePlayers
                        .selectAll()
                        .where { GamePlayers.gameId eq gameId }
                        .count()
                    if (playerCount >= game[Games.maxPlayers]) {
                        throw IllegalStateException("La partida está llena")
                    }

                    val usedIndices = GamePlayers
                        .selectAll()
                        .where { GamePlayers.gameId eq gameId }
                        .map { it[GamePlayers.playerIndex] }

                    val usedCharacters = GamePlayers
                        .selectAll()
                        .where { GamePlayers.gameId eq gameId }
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

                    // Verificar si la partida se llenó e iniciarla automáticamente
                    startGameIfFull(gameId)
                }

                call.respondText("Te has unido a la partida", status = HttpStatusCode.OK)

            } catch (e: NoSuchElementException) {
                call.respondText(e.message ?: "Partida no encontrada", status = HttpStatusCode.NotFound)
            } catch (e: IllegalStateException) {
                call.respondText(e.message ?: "Error de validación", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                println("❌ Error en /games/join: ${e.message}")
                e.printStackTrace()
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
                    val players = GamePlayers
                        .selectAll()
                        .where { GamePlayers.gameId eq gameId }
                        .toList()
                    val allReady = players.all { it[GamePlayers.isReady] }
                    val game = Games
                        .selectAll()
                        .where { Games.id eq gameId }
                        .single()
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
                        val deck = GameDecks
                            .selectAll()
                            .where { GameDecks.gameId eq gameId }
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
                println("❌ Error en /games/ready: ${e.message}")
                e.printStackTrace()
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER ESTADO DE LA PARTIDA
        get("/{gameId}") {
            try {
                val gameId = UUID.fromString(call.parameters["gameId"])

                val gameState = transaction {
                    val game = Games
                        .selectAll()
                        .where { Games.id eq gameId }
                        .singleOrNull()
                        ?: throw NoSuchElementException("Partida no encontrada")

                    val players = GamePlayers
                        .innerJoin(Users)
                        .selectAll()
                        .where { GamePlayers.gameId eq gameId }
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
                println("❌ Error en GET /games/{gameId}: ${e.message}")
                e.printStackTrace()
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
                    val game = Games
                        .selectAll()
                        .where { Games.id eq gameId }
                        .singleOrNull()
                        ?: throw NoSuchElementException("Partida no encontrada")

                    if (game[Games.status] != "IN_PROGRESS") {
                        throw IllegalStateException("La partida no está en progreso")
                    }

                    val player = GamePlayers
                        .selectAll()
                        .where { (GamePlayers.gameId eq gameId) and (GamePlayers.userId eq userId) }
                        .singleOrNull() ?: throw IllegalStateException("No estás en esta partida")

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
                    val playerCount = GamePlayers
                        .selectAll()
                        .where { GamePlayers.gameId eq gameId }
                        .count()
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
                println("❌ Error en /games/roll-dice: ${e.message}")
                e.printStackTrace()
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER PARTIDAS ACTIVAS DEL USUARIO
        get("/active") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())

                val activeGames = transaction {
                    // Buscar partidas donde el usuario es jugador y el estado no es FINISHED
                    val userGames = GamePlayers
                        .innerJoin(Games)
                        .selectAll()
                        .where { (GamePlayers.userId eq userId) and (Games.status neq "FINISHED") }
                        .map { row ->
                            val gameId = row[Games.id]

                            // Obtener todos los jugadores de esta partida
                            val players = GamePlayers
                                .innerJoin(Users)
                                .selectAll()
                                .where { GamePlayers.gameId eq gameId }
                                .orderBy(GamePlayers.playerIndex to SortOrder.ASC)
                                .map { playerRow ->
                                    mapOf(
                                        "id" to playerRow[GamePlayers.id].toString(),
                                        "userId" to playerRow[GamePlayers.userId].toString(),
                                        "username" to playerRow[Users.username],
                                        "playerIndex" to playerRow[GamePlayers.playerIndex],
                                        "character" to playerRow[GamePlayers.character],
                                        "position" to playerRow[GamePlayers.position],
                                        "isReady" to playerRow[GamePlayers.isReady],
                                        "connected" to playerRow[GamePlayers.connected]
                                    )
                                }

                            mapOf(
                                "id" to gameId.toString(),
                                "code" to row[Games.code],
                                "maxPlayers" to row[Games.maxPlayers],
                                "status" to row[Games.status],
                                "isStarted" to row[Games.isStarted],
                                "players" to players
                            )
                        }

                    userGames
                }

                call.respond(activeGames)

            } catch (e: Exception) {
                println("❌ Error en /games/active: ${e.message}")
                e.printStackTrace()
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // SALIR DE UNA PARTIDA
        post("/{gameId}/leave") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val gameId = UUID.fromString(call.parameters["gameId"])

                val message = transaction {
                    // Verificar que el usuario está en la partida
                    val playerRecord = GamePlayers
                        .selectAll()
                        .where { (GamePlayers.gameId eq gameId) and (GamePlayers.userId eq userId) }
                        .singleOrNull()

                    if (playerRecord == null) {
                        throw IllegalStateException("No estás en esta partida")
                    }

                    val playerId = playerRecord[GamePlayers.id]

                    // 1. Eliminar mano del jugador
                    PlayerHands.deleteWhere {
                        (PlayerHands.gameId eq gameId) and (PlayerHands.playerId eq playerId)
                    }

                    // 2. Eliminar al jugador de la partida
                    GamePlayers.deleteWhere {
                        (GamePlayers.gameId eq gameId) and (GamePlayers.userId eq userId)
                    }

                    // 3. Verificar si quedan jugadores
                    val remainingPlayers = GamePlayers
                        .selectAll()
                        .where { GamePlayers.gameId eq gameId }
                        .count()

                    if (remainingPlayers == 0L) {
                        // Si no quedan jugadores, eliminar la partida completa
                        GameDecks.deleteWhere { GameDecks.gameId eq gameId }
                        Games.deleteWhere { Games.id eq gameId }
                        "Partida eliminada (eras el último jugador)"
                    } else {
                        // Si quedan jugadores, reorganizar índices
                        val game = Games
                            .selectAll()
                            .where { Games.id eq gameId }
                            .singleOrNull()

                        if (game != null && game[Games.status] == "WAITING") {
                            // Obtener jugadores restantes ordenados por índice
                            val remainingPlayersList = GamePlayers
                                .selectAll()
                                .where { GamePlayers.gameId eq gameId }
                                .orderBy(GamePlayers.playerIndex to SortOrder.ASC)
                                .toList()

                            // Actualizar índices para que sean consecutivos
                            remainingPlayersList.forEachIndexed { index, player ->
                                GamePlayers.update({ GamePlayers.id eq player[GamePlayers.id] }) {
                                    it[playerIndex] = index
                                }
                            }
                        }
                        "Has salido de la partida exitosamente"
                    }
                }

                call.respondText(message, status = HttpStatusCode.OK)

            } catch (e: IllegalStateException) {
                println("❌ Error de validación en /games/{gameId}/leave: ${e.message}")
                call.respondText(e.message ?: "Error de validación", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                println("❌ Error en /games/{gameId}/leave: ${e.message}")
                e.printStackTrace()
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
                    val player = GamePlayers
                        .selectAll()
                        .where { (GamePlayers.gameId eq gameId) and (GamePlayers.userId eq userId) }
                        .singleOrNull() ?: throw IllegalStateException("No estás en esta partida")

                    val playerHand = PlayerHands
                        .selectAll()
                        .where { (PlayerHands.gameId eq gameId) and (PlayerHands.playerId eq player[GamePlayers.id]) }
                        .singleOrNull()

                    val cards = playerHand?.get(PlayerHands.cards)
                        ?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.map { it.toInt() } ?: emptyList()

                    mapOf("cards" to cards)
                }

                call.respond(hand)

            } catch (e: Exception) {
                println("❌ Error en /games/{gameId}/hand: ${e.message}")
                e.printStackTrace()
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // LISTAR PARTIDAS DISPONIBLES
        get("/available") {
            try {
                val games = transaction {
                    Games
                        .selectAll()
                        .where { Games.status eq "WAITING" }
                        .mapNotNull { game ->
                            val gameId = game[Games.id]
                            val currentPlayers = GamePlayers
                                .selectAll()
                                .where { GamePlayers.gameId eq gameId }
                                .count()
                            val maxPlayers = game[Games.maxPlayers]

                            if (currentPlayers < maxPlayers) {
                                mapOf(
                                    "id" to gameId.toString(),
                                    "code" to game[Games.code],
                                    "maxPlayers" to maxPlayers,
                                    "currentPlayers" to currentPlayers
                                )
                            } else null
                        }
                }
                call.respond(games)
            } catch (e: Exception) {
                println("❌ Error en /games/available: ${e.message}")
                e.printStackTrace()
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}
