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
        SchemaUtils.create(Users, Games, Cards, BoardSquares)

        // 2. Crear tablas que dependen de las anteriores
        SchemaUtils.create(GamePlayers)

        // 3. Crear el resto de tablas
        SchemaUtils.create(ChatMessages, GameDecks, PlayerHands, PlayerEffects)

        // 4. Inicializar datos dentro de la misma transacción
        // Inicializar las 21 cartas
        val existingCards = Cards.selectAll().count()
        if (existingCards == 0L) {
            CardInitializer.initializeCardsData()
            println("21 cartas inicializadas correctamente")
        } else {
            println("Las cartas ya están inicializadas")
        }

        // Inicializar las 113 casillas del tablero
        val existingSquares = BoardSquares.selectAll().count()
        if (existingSquares == 0L) {
            CardInitializer.initializeBoardSquaresData()
            println("113 casillas del tablero inicializadas correctamente (0-112)")
        } else {
            println("Las casillas del tablero ya están inicializadas")
        }
    }

    println("Base de datos configurada, cartas y tablero inicializados correctamente")
}