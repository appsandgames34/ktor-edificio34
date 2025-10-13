package com.appsandgames34.routes

import com.appsandgames34.modelos.Users
import com.appsandgames34.util.JwtConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

data class RegisterRequest(val username: String, val password: String)
data class LoginRequest(val username: String, val password: String)

fun Route.userRoutes() {

    route("/users") {

        post("/register") {
            val req = call.receive<RegisterRequest>()
            val exists = transaction {
                Users.select ( Users.username eq req.username ).count() > 0
            }

            if (exists) {
                call.respondText("Usuario ya existe", status = HttpStatusCode.Conflict)
            } else {
                val userIdResult = transaction {
                    Users.insert {
                        it[id] = UUID.randomUUID() // Asignamos explícitamente el UUID
                        it[username] = req.username
                        it[email] = req.username // Usamos username como email, tal como estaba
                        it[passwordHash] = req.password
                        it[createdAt] = LocalDateTime.now() // Usamos LocalDateTime
                    }.resultedValues?.firstOrNull()
                }

                if (userIdResult == null) {
                    call.respondText("Fallo al crear usuario", status = HttpStatusCode.InternalServerError)
                    return@post
                }

                // Extraemos el valor del UUID
                val userId = userIdResult[Users.id]

                val token = JwtConfig.generateToken(userId, req.username)
                call.respond(mapOf("token" to token))
            }
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val user = transaction {
                // Seleccionamos explícitamente las columnas que necesitamos
                Users
                    .select(Users.id, Users.username) // Seleccionamos solo las columnas necesarias
                    .where { (Users.username eq req.username) and (Users.passwordHash eq req.password) }
                    .map { row ->
                        // extraemos el id como UUID y el username como String
                        val userId: UUID = row[Users.id]
                        val username: String = row[Users.username]
                        userId to username
                    }
                    .singleOrNull()
            }

            if (user != null) {
                val (userId, username) = user
                // JwtConfig.generateToken ahora espera un UUID para userId
                val token = JwtConfig.generateToken(userId, username)
                call.respond(mapOf("token" to token))
            } else {
                call.respondText("Credenciales inválidas", status = HttpStatusCode.Unauthorized)
            }
        }

    }

}
