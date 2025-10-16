package com.appsandgames34.routes

import com.appsandgames34.modelos.Users
import com.appsandgames34.modelos.UserSessions
import com.appsandgames34.util.JwtConfig
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
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import java.util.*

data class RegisterRequest(val username: String, val email: String, val password: String)
data class LoginRequest(val username: String, val password: String, val deviceId: String? = null)

fun Route.userRoutes() {
    route("/users") {

        // REGISTRO
        post("/register") {
            try {
                val req = call.receive<RegisterRequest>()

                // Validaciones básicas
                if (req.username.length < 3) {
                    call.respondText("El nombre de usuario debe tener al menos 3 caracteres", status = HttpStatusCode.BadRequest)
                    return@post
                }
                if (req.password.length < 6) {
                    call.respondText("La contraseña debe tener al menos 6 caracteres", status = HttpStatusCode.BadRequest)
                    return@post
                }

                val exists = transaction {
                    Users.selectAll()
                        .where { (Users.username eq req.username) or (Users.email eq req.email) }
                        .count() > 0
                }

                if (exists) {
                    call.respondText("El usuario o email ya existe", status = HttpStatusCode.Conflict)
                    return@post
                }

                val userId = transaction {
                    // Hash de la contraseña usando BCrypt
                    val hashedPassword = BCrypt.hashpw(req.password, BCrypt.gensalt())

                    val result = Users.insert {
                        it[id] = UUID.randomUUID()
                        it[username] = req.username
                        it[email] = req.email
                        it[passwordHash] = hashedPassword
                        it[createdAt] = LocalDateTime.now()
                    }.resultedValues?.firstOrNull()

                    result?.get(Users.id) ?: throw Exception("Error al crear usuario")
                }

                val token = JwtConfig.generateToken(userId, req.username)
                call.respond(mapOf(
                    "token" to token,
                    "userId" to userId.toString(),
                    "username" to req.username
                ))

            } catch (e: Exception) {
                call.respondText("Error en el registro: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // LOGIN (con gestión de sesiones)
        post("/login") {
            try {
                val req = call.receive<LoginRequest>()
                val deviceId = req.deviceId ?: UUID.randomUUID().toString() // Generar deviceId si no se proporciona

                val user = transaction {
                    Users.selectAll()
                        .where { Users.username eq req.username }
                        .map { row ->
                            Triple(
                                row[Users.id],
                                row[Users.username],
                                row[Users.passwordHash]
                            )
                        }
                        .singleOrNull()
                }

                if (user != null) {
                    val (userId, username, passwordHash) = user

                    // Verificar contraseña con BCrypt
                    if (BCrypt.checkpw(req.password, passwordHash)) {

                        // Verificar si el usuario ya tiene una sesión activa
                        val activeSession = transaction {
                            UserSessions.selectAll()
                                .where { UserSessions.userId eq userId }
                                .singleOrNull()
                        }

                        if (activeSession != null) {
                            // Hay sesión activa → invalidar sesión anterior
                            transaction {
                                UserSessions.deleteWhere { UserSessions.userId eq userId }
                            }
                            println("⚠️ Sesión anterior de usuario $username invalidada")
                        }

                        // Crear nuevo token
                        val token = JwtConfig.generateToken(userId, username)

                        // Crear nueva sesión
                        transaction {
                            UserSessions.insert {
                                it[UserSessions.id] = UUID.randomUUID()
                                it[UserSessions.userId] = userId
                                it[UserSessions.deviceId] = deviceId
                                it[UserSessions.token] = token
                                it[UserSessions.lastActivityAt] = LocalDateTime.now()
                                it[UserSessions.createdAt] = LocalDateTime.now()
                            }
                        }

                        println("✅ Nuevo login de $username desde dispositivo $deviceId")

                        call.respond(mapOf(
                            "token" to token,
                            "userId" to userId.toString(),
                            "username" to username,
                            "sessionCreated" to true
                        ))
                    } else {
                        call.respondText("Credenciales inválidas", status = HttpStatusCode.Unauthorized)
                    }
                } else {
                    call.respondText("Credenciales inválidas", status = HttpStatusCode.Unauthorized)
                }

            } catch (e: Exception) {
                println("❌ Error en el login: ${e.message}")
                e.printStackTrace()
                call.respondText("Error en el login: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // LOGOUT
        post("/logout") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())

                val deleted = transaction {
                    UserSessions.deleteWhere { UserSessions.userId eq userId }
                }

                if (deleted > 0) {
                    println("✅ Sesión de usuario $userId cerrada")
                    call.respondText("Sesión cerrada exitosamente", status = HttpStatusCode.OK)
                } else {
                    call.respondText("No hay sesión activa", status = HttpStatusCode.NotFound)
                }

            } catch (e: Exception) {
                println("❌ Error en logout: ${e.message}")
                e.printStackTrace()
                call.respondText("Error al cerrar sesión: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }

        // OBTENER PERFIL (Requiere autenticación)
        authenticate("auth-jwt") {
            get("/profile") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())

                    val profile = transaction {
                        Users.select ( Users.id eq userId )
                            .map { row ->
                                mapOf(
                                    "id" to row[Users.id].toString(),
                                    "username" to row[Users.username],
                                    "email" to row[Users.email],
                                    "createdAt" to row[Users.createdAt].toString()
                                )
                            }
                            .singleOrNull()
                    }

                    if (profile != null) {
                        call.respond(profile)
                    } else {
                        call.respondText("Usuario no encontrado", status = HttpStatusCode.NotFound)
                    }

                } catch (e: Exception) {
                    call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}