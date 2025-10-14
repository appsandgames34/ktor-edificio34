package com.appsandgames34.routes

import com.appsandgames34.modelos.*
import com.appsandgames34.routes.GameConnectionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

data class PlayCardRequest(val gameId: String, val cardId: Int, val targetPlayerId: String? = null)
data class DrawCardRequest(val gameId: String)

fun Route.cardRoutes() {
    route("/cards") {

        // JUGAR CARTA
        post("/play") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val req = call.receive<PlayCardRequest>()
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

                    // Obtener la mano del jugador
                    val playerHand = PlayerHands.select(
                        (PlayerHands.gameId eq gameId) and (PlayerHands.playerId eq player[GamePlayers.id])
                    ).singleOrNull() ?: throw IllegalStateException("No se encontró tu mano")

                    val cards = playerHand[PlayerHands.cards]
                        .split(",")
                        .filter { it.isNotEmpty() }
                        .map { it.toInt() }
                        .toMutableList()

                    if (req.cardId !in cards) {
                        throw IllegalStateException("No tienes esa carta en tu mano")
                    }

                    // Remover la carta de la mano
                    cards.remove(req.cardId)

                    // Actualizar la mano
                    PlayerHands.update({
                        (PlayerHands.gameId eq gameId) and (PlayerHands.playerId eq player[GamePlayers.id])
                    }) {
                        it[PlayerHands.cards] = cards.joinToString(",")
                    }

                    // Añadir carta al descarte
                    val deck = GameDecks.select(GameDecks.gameId eq gameId).single()
                    val currentDiscard = deck[GameDecks.discard] ?: ""
                    val newDiscard = if (currentDiscard.isEmpty()) {
                        req.cardId.toString()
                    } else {
                        "$currentDiscard,${req.cardId}"
                    }

                    GameDecks.update({ GameDecks.gameId eq gameId }) {
                        it[discard] = newDiscard
                    }

                    // Obtener información de la carta
                    val cardInfo = Cards.select(Cards.id eq req.cardId).singleOrNull()

                    mapOf(
                        "success" to true,
                        "cardId" to req.cardId,
                        "cardName" to (cardInfo?.get(Cards.name) ?: "Desconocida"),
                        "cardsInHand" to cards.size
                    )
                }

                // Notificar a todos los jugadores vía WebSocket
                GameConnectionManager.broadcast(req.gameId, """
                    {
                        "type": "card_played",
                        "userId": "$userId",
                        "cardId": ${req.cardId},
                        "timestamp": ${System.currentTimeMillis()}
                    }
                """.trimIndent())

                call.respond(result)

            } catch (e: IllegalStateException) {
                call.respondText(e.message ?: "Error", status = HttpStatusCode.BadRequest)
            } catch (e: NoSuchElementException) {
                call.respondText(e.message ?: "No encontrado", status = HttpStatusCode.NotFound)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // ROBAR CARTA
        post("/draw") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val req = call.receive<DrawCardRequest>()
                val gameId = UUID.fromString(req.gameId)

                val result = transaction {
                    val player = GamePlayers.select(
                        (GamePlayers.gameId eq gameId) and (GamePlayers.userId eq userId)
                    ).singleOrNull() ?: throw IllegalStateException("No estás en esta partida")

                    // Obtener la mano del jugador
                    val playerHand = PlayerHands.select(
                        (PlayerHands.gameId eq gameId) and (PlayerHands.playerId eq player[GamePlayers.id])
                    ).singleOrNull() ?: throw IllegalStateException("No se encontró tu mano")

                    val cards = playerHand[PlayerHands.cards]
                        .split(",")
                        .filter { it.isNotEmpty() }
                        .map { it.toInt() }
                        .toMutableList()

                    if (cards.size >= 3) {
                        throw IllegalStateException("Ya tienes 3 cartas en tu mano")
                    }

                    // Obtener el mazo
                    val deck = GameDecks.select(GameDecks.gameId eq gameId).single()
                    val deckCards = deck[GameDecks.deckCards]
                        .split(",")
                        .filter { it.isNotEmpty() }
                        .map { it.toInt() }
                        .toMutableList()

                    if (deckCards.isEmpty()) {
                        // Si el mazo está vacío, barajar el descarte
                        val discard = deck[GameDecks.discard]
                            ?.split(",")
                            ?.filter { it.isNotEmpty() }
                            ?.map { it.toInt() }
                            ?.shuffled()
                            ?: throw IllegalStateException("No hay cartas disponibles")

                        deckCards.addAll(discard)

                        GameDecks.update({ GameDecks.gameId eq gameId }) {
                            it[GameDecks.discard] = ""
                        }
                    }

                    val drawnCard = deckCards.removeFirst()
                    cards.add(drawnCard)

                    // Actualizar la mano
                    PlayerHands.update({
                        (PlayerHands.gameId eq gameId) and (PlayerHands.playerId eq player[GamePlayers.id])
                    }) {
                        it[PlayerHands.cards] = cards.joinToString(",")
                    }

                    // Actualizar el mazo
                    GameDecks.update({ GameDecks.gameId eq gameId }) {
                        it[GameDecks.deckCards] = deckCards.joinToString(",")
                    }

                    mapOf(
                        "cardId" to drawnCard,
                        "cardsInHand" to cards.size,
                        "cardsInDeck" to deckCards.size
                    )
                }

                call.respond(result)

            } catch (e: IllegalStateException) {
                call.respondText(e.message ?: "Error", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER INFORMACIÓN DE TODAS LAS CARTAS
        get("/info") {
            try {
                val cardsInfo = transaction {
                    Cards.selectAll()
                        .orderBy(Cards.id)
                        .map {
                            mapOf(
                                "id" to it[Cards.id],
                                "name" to it[Cards.name],
                                "description" to it[Cards.description],
                                "properties" to it[Cards.properties]
                            )
                        }
                }
                call.respond(cardsInfo)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER INFORMACIÓN DE UNA CARTA ESPECÍFICA
        get("/info/{cardId}") {
            try {
                val cardId = call.parameters["cardId"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("ID de carta inválido")

                val cardInfo = transaction {
                    Cards.selectAll()
                        .where { Cards.id eq cardId }
                        .map {
                            mapOf(
                                "id" to it[Cards.id],
                                "name" to it[Cards.name],
                                "description" to it[Cards.description],
                                "properties" to it[Cards.properties]
                            )
                        }
                        .singleOrNull()
                } ?: throw NoSuchElementException("Carta no encontrada")

                call.respond(cardInfo)

            } catch (e: IllegalArgumentException) {
                call.respondText(e.message ?: "Argumento inválido", status = HttpStatusCode.BadRequest)
            } catch (e: NoSuchElementException) {
                call.respondText(e.message ?: "No encontrado", status = HttpStatusCode.NotFound)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}