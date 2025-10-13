package com.appsandgames34.routes

import com.appsandgames34.modelos.ChatMessages
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

data class ChatMessageRequest(val gameId: String, val playerId: String?, val message: String)

fun Route.chatRoutes() {

    route("/chat") {

        post("/send") {
            val req = call.receive<ChatMessageRequest>()
            transaction {
                ChatMessages.insert {
                    it[id] = UUID.randomUUID()
                    it[gameId] = UUID.fromString(req.gameId)
                    it[playerId] = req.playerId?.let { UUID.fromString(it) }
                    it[message] = req.message
                    it[createdAt] = LocalDateTime.now()
                }
            }
            call.respondText("Mensaje enviado")
        }

        get("/messages/{gameId}") {
            val gameId = call.parameters["gameId"] ?: return@get call.respondText("Falta gameId")
            val messages = transaction {
                ChatMessages.select ( ChatMessages.gameId eq UUID.fromString(gameId) )
                    .orderBy(ChatMessages.createdAt to SortOrder.ASC)
                    .map {
                        mapOf(
                            "playerId" to it[ChatMessages.playerId]?.toString(),
                            "message" to it[ChatMessages.message],
                            "createdAt" to it[ChatMessages.createdAt].toString()
                        )
                    }
            }
            call.respond(messages)
        }
    }
}
