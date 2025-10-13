package com.appsandgames34.util

import com.appsandgames34.modelos.Cards
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object CardInitializer {

    fun initializeCards() {
        transaction {
            // Verificar si ya hay cartas
            val existingCards = Cards.selectAll().count()
            if (existingCards > 0) {
                println("Las cartas ya están inicializadas")
                return@transaction
            }

            // Lista de las 20 cartas del juego
            val cardsList = listOf(
                Triple(1, "Avanzar +5", """{"effect": "advance", "value": 5}"""),
                Triple(2, "Avanzar +10", """{"effect": "advance", "value": 10}"""),
                Triple(3, "Retroceder -5", """{"effect": "retreat", "value": 5}"""),
                Triple(4, "Retroceder -10", """{"effect": "retreat", "value": 10}"""),
                Triple(5, "Cambiar Posición", """{"effect": "swap_position", "type": "player"}"""),
                Triple(6, "Teletransporte", """{"effect": "teleport", "range": "random"}"""),
                Triple(7, "Escudo", """{"effect": "shield", "duration": 1}"""),
                Triple(8, "Congelar Jugador", """{"effect": "freeze", "duration": 1}"""),
                Triple(9, "Doble Turno", """{"effect": "extra_turn", "count": 1}"""),
                Triple(10, "Saltar Turno", """{"effect": "skip_turn", "target": "other"}"""),
                Triple(11, "Crash", """{"effect": "counter", "cancels": "any"}"""),
                Triple(12, "Robar Carta", """{"effect": "steal_card", "target": "player"}"""),
                Triple(13, "Todos Atrás -3", """{"effect": "all_retreat", "value": 3}"""),
                Triple(14, "Todos Adelante +3", """{"effect": "all_advance", "value": 3}"""),
                Triple(15, "Invertir Orden", """{"effect": "reverse_order"}"""),
                Triple(16, "Llave Maestra", """{"effect": "key", "required": true}"""),
                Triple(17, "Trampa", """{"effect": "trap", "penalty": -5}"""),
                Triple(18, "Multiplicador x2", """{"effect": "multiplier", "value": 2}"""),
                Triple(19, "Carta Comodín", """{"effect": "wildcard", "flexible": true}"""),
                Triple(20, "Retorno al Inicio", """{"effect": "reset_position"}""")
            )

            cardsList.forEach { (id, name, properties) ->
                Cards.insert {
                    it[Cards.id] = id
                    it[Cards.name] = name
                    it[Cards.description] = generateDescription(id, name)
                    it[Cards.properties] = properties
                }
            }

            println("${cardsList.size} cartas inicializadas correctamente")
        }
    }

    private fun generateDescription(id: Int, name: String): String {
        return when (id) {
            1 -> "Avanza 5 casillas en el tablero. Perfecto para acercarte a la meta."
            2 -> "Avanza 10 casillas. Un gran salto hacia la victoria."
            3 -> "Retrocede 5 casillas. Úsala estratégicamente contra otros."
            4 -> "Retrocede 10 casillas. Puede hacer que un oponente pierda terreno."
            5 -> "Intercambia tu posición con otro jugador. Elige sabiamente."
            6 -> "Teletranspórtate a una casilla aleatoria del tablero."
            7 -> "Protégete de la próxima carta negativa que te jueguen."
            8 -> "Congela a un jugador durante su próximo turno."
            9 -> "Juega dos turnos seguidos. Duplica tus posibilidades."
            10 -> "Haz que otro jugador pierda su próximo turno."
            11 -> "Carta especial: Cancela cualquier carta que te hayan jugado. ¡Salvación instantánea!"
            12 -> "Roba una carta aleatoria de la mano de otro jugador."
            13 -> "Todos los jugadores retroceden 3 casillas excepto tú."
            14 -> "Todos los jugadores avanzan 3 casillas. ¡Generosidad grupal!"
            15 -> "Invierte el orden de los turnos del juego."
            16 -> "La Llave Maestra: Necesaria para ganar al llegar a la casilla 112."
            17 -> "Coloca una trampa. El próximo jugador que pase retrocede 5 casillas."
            18 -> "Multiplica por 2 el resultado de tus próximos dados."
            19 -> "Comodín: Puede copiar el efecto de cualquier carta en juego."
            20 -> "Vuelve a la casilla de inicio. Úsala estratégicamente en otros."
            else -> "Descripción no disponible"
        }
    }
}