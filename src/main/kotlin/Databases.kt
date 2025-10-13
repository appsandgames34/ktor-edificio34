package com.appsandgames34

import com.appsandgames34.modelos.Cards
import com.appsandgames34.modelos.ChatMessages
import com.appsandgames34.modelos.GameDecks
import com.appsandgames34.modelos.GamePlayers
import com.appsandgames34.modelos.Games
import com.appsandgames34.modelos.PlayerHands
import com.appsandgames34.modelos.Users
import com.fasterxml.jackson.databind.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.sql.Connection
import java.sql.DriverManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.*

lateinit var database: Database

fun Application.configureDatabases() {
    database = Database.connect(
        url = environment.config.property("postgres.url").getString(),
        user = environment.config.property("postgres.user").getString(),
        password = environment.config.property("postgres.password").getString(),
        driver = "org.postgresql.Driver"
    )
    transaction(database) {
        // 1. Create all Independent Parent Tables (Users MUST be here)
        SchemaUtils.create(Users, Games, Cards)

        // 2. Create GamePlayers, which references Users.
        SchemaUtils.create(GamePlayers)

        // 3. Create the rest.
        SchemaUtils.create(ChatMessages, GameDecks, PlayerHands)
    }
}
