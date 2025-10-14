package com.appsandgames34.routes

import com.appsandgames34.modelos.BoardSquares
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.boardRoutes() {
    route("/board") {

        // OBTENER TODAS LAS CASILLAS DEL TABLERO
        get("/squares") {
            try {
                val squares = transaction {
                    BoardSquares.selectAll()
                        .orderBy(BoardSquares.position)
                        .map {
                            mapOf(
                                "position" to it[BoardSquares.position],
                                "type" to it[BoardSquares.type],
                                "floor" to it[BoardSquares.floor],
                                "description" to it[BoardSquares.description]
                            )
                        }
                }
                call.respond(squares)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER CASILLA ESPECÍFICA
        get("/squares/{position}") {
            try {
                val position = call.parameters["position"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Posición inválida")

                if (position !in 0..112) {
                    throw IllegalArgumentException("La posición debe estar entre 0 y 112")
                }

                val square = transaction {
                    BoardSquares.select ( BoardSquares.position eq position )
                        .map {
                            mapOf(
                                "position" to it[BoardSquares.position],
                                "type" to it[BoardSquares.type],
                                "floor" to it[BoardSquares.floor],
                                "description" to it[BoardSquares.description]
                            )
                        }
                        .singleOrNull()
                } ?: throw NoSuchElementException("Casilla no encontrada")

                call.respond(square)

            } catch (e: IllegalArgumentException) {
                call.respondText(e.message ?: "Argumento inválido", status = HttpStatusCode.BadRequest)
            } catch (e: NoSuchElementException) {
                call.respondText(e.message ?: "No encontrado", status = HttpStatusCode.NotFound)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER CASILLAS ESPECIALES (rellanos, ascensores, etc)
        get("/special-squares") {
            try {
                val specialSquares = transaction {
                    BoardSquares.select ( BoardSquares.type neq "NORMAL" )
                        .orderBy(BoardSquares.position)
                        .map {
                            mapOf(
                                "position" to it[BoardSquares.position],
                                "type" to it[BoardSquares.type],
                                "floor" to it[BoardSquares.floor],
                                "description" to it[BoardSquares.description]
                            )
                        }
                }
                call.respond(specialSquares)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER RELLANOS DE ASCENSOR POR PLANTA
        get("/elevator-landings") {
            try {
                val elevatorLandings = transaction {
                    BoardSquares.select ( BoardSquares.type eq "ELEVATOR_LANDING" )
                        .orderBy(BoardSquares.position)
                        .map {
                            mapOf(
                                "position" to it[BoardSquares.position],
                                "floor" to it[BoardSquares.floor],
                                "description" to it[BoardSquares.description]
                            )
                        }
                }
                call.respond(elevatorLandings)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // CALCULAR PLANTA ACTUAL BASADA EN POSICIÓN
        get("/calculate-floor/{position}") {
            try {
                val position = call.parameters["position"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Posición inválida")

                if (position !in 0..112) {
                    throw IllegalArgumentException("La posición debe estar entre 0 y 112")
                }

                val floor = when {
                    position == 0 -> 0
                    position in 1..15 -> 0
                    position in 16..31 -> 1
                    position in 32..47 -> 2
                    position in 48..63 -> 3
                    position in 64..79 -> 4
                    position in 80..96 -> 5
                    position in 97..112 -> 6
                    else -> null
                }

                call.respond(mapOf(
                    "position" to position,
                    "floor" to floor,
                    "isElevatorLanding" to (position in listOf(16, 32, 48, 64, 80, 97)),
                    "isLanding" to (position in listOf(8, 24, 40, 56, 72, 88, 105)),
                    "isExit" to (position == 112)
                ))

            } catch (e: IllegalArgumentException) {
                call.respondText(e.message ?: "Argumento inválido", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}