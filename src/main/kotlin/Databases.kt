package com.appsandgames34

import com.appsandgames34.modelos.*
import com.appsandgames34.util.CardInitializer
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

lateinit var database: Database

fun Application.configureDatabases() {
    database = Database.connect(
        url = environment.config.property("postgres.url").getString(),
        user = environment.config.property("postgres.user").getString(),
        password = environment.config.property("postgres.password").getString(),
        driver = "org.postgresql.Driver"
    )

    transaction(database) {
        // Habilitar logs SQL (opcional, útil para desarrollo)
        addLogger(StdOutSqlLogger)

        // 1. Crear tablas independientes primero
        SchemaUtils.create(Users, Games, Cards)

        // 2. Crear tablas que dependen de las anteriores
        SchemaUtils.create(GamePlayers)

        // 3. Crear el resto de tablas
        SchemaUtils.create(ChatMessages, GameDecks, PlayerHands)
    }

    // Inicializar las 21 cartas y 112 casillas del juego
    CardInitializer.initializeCards()
    CardInitializer.initializeBoardSquares()


    println("Base de datos configurada y cartas inicializadas correctamente")
}