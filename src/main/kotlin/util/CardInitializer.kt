package com.appsandgames34.util

import com.appsandgames34.modelos.BoardSquares
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

            // Lista de las 21 cartas del juego
            val cardsList = listOf(
                Triple(1, "Llave", """{"effect": "key", "required": true, "isSpecial": false}"""),
                Triple(2, "Especial Alarma", """{"effect": "alarm_all_to_start", "excludeOwner": true, "isSpecial": true, "canBeCrashed": true, "crashedBy": [3]}"""),
                Triple(3, "Te Pillé (Crash Alarma)", """{"effect": "crash_alarm", "crashTarget": 2, "isSpecial": false}"""),
                Triple(4, "Zapatillas Aladas", """{"effect": "multiply_dice", "multiplier": 3, "isSpecial": false, "canBeCrashed": true, "crashedBy": [5]}"""),
                Triple(5, "Tijeretazo (Crash Zapatillas)", """{"effect": "crash_winged_shoes", "crashTarget": 4, "reduceTo": "single_dice_x3", "isSpecial": false}"""),
                Triple(6, "Recién Fregado", """{"effect": "block_square", "duration": 1, "isSpecial": false}"""),
                Triple(7, "Tirada Doble", """{"effect": "double_roll", "count": 2, "isSpecial": false}"""),
                Triple(8, "Entrega de Paquete", """{"effect": "go_down_floors", "floors": 2, "targetOther": true, "isSpecial": false}"""),
                Triple(9, "Crossfitter", """{"effect": "single_dice", "isSpecial": false}"""),
                Triple(10, "Subiendo", """{"effect": "go_up_floor", "requiresFloorLanding": true, "isSpecial": false, "canBeCrashed": true, "crashedBy": [11]}"""),
                Triple(11, "Vecino Maravilloso (Crash Subiendo)", """{"effect": "crash_go_up", "crashTarget": 10, "isSpecial": false}"""),
                Triple(12, "Especial Cuarentena", """{"effect": "quarantine_end_game", "endGame": true, "noWinner": true, "isSpecial": true}"""),
                Triple(13, "Gatito que Ronronea", """{"effect": "place_cat", "diceRoll": true, "lowRoll": "down", "highRoll": "up", "isSpecial": false}"""),
                Triple(14, "Fiesta", """{"effect": "all_to_floor", "diceRoll": true, "isSpecial": false, "canBeCrashed": true, "crashedBy": [15]}"""),
                Triple(15, "Antisocial (Crash Fiesta)", """{"effect": "crash_party", "crashTarget": 14, "stayInPlace": true, "isSpecial": false}"""),
                Triple(16, "A Ciegas", """{"effect": "blackout", "requiresFlashlight": true, "isSpecial": false}"""),
                Triple(17, "Linterna (Crash A Ciegas)", """{"effect": "flashlight", "crashTarget": 16, "allowMovement": true, "isSpecial": false}"""),
                Triple(18, "Catapún", """{"effect": "fall_down_landing", "isSpecial": false}"""),
                Triple(19, "Chisme", """{"effect": "gossip", "pullToPosition": true, "skipTurn": true, "targetOther": true, "isSpecial": false}"""),
                Triple(20, "Intercambio", """{"effect": "swap_hands", "targetOther": true, "isSpecial": false}"""),
                Triple(21, "News", """{"effect": "none", "isSpecial": false}""")
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
            1 -> "Carta necesaria para ganar. Debes tener esta carta y llegar a la casilla 112 (Puerta de Salida)."
            2 -> "¡ALARMA! Todos los jugadores van a la casilla de inicio (casilla 0) excepto tú. Puede ser contrarrestada con 'Te Pillé'."
            3 -> "Carta de defensa. Úsala cuando alguien juegue 'Especial Alarma' para quedarte en tu sitio y no bajar."
            4 -> "Multiplica tu tirada de dados por 3. ¡Avanza muy rápido! Puede ser contrarrestada con 'Tijeretazo'."
            5 -> "Contrarresta 'Zapatillas Aladas'. El rival solo usará un dado multiplicado por 3."
            6 -> "Bloquea una casilla del tablero. Ningún jugador puede pasar por ella durante una ronda."
            7 -> "¡Tirada doble! Este turno tiras los dados 2 veces."
            8 -> "Elige a un jugador para que baje 2 plantas a entregar un paquete."
            9 -> "Este turno solo tiras con un dado. Úsala estratégicamente."
            10 -> "Si estás en el rellano de una planta, sube a la siguiente planta y continúa tu turno. Puede ser contrarrestada con 'Vecino Maravilloso'."
            11 -> "Contrarresta 'Subiendo'. Cancela la subida de planta del rival."
            12 -> "¡CUARENTENA! El edificio entra en cuarentena, la partida se acaba y nadie gana."
            13 -> "Coloca un gato en la casilla que quieras. El primero que pase lanzará un dado: 1-3 baja al rellano inferior, 4-6 sube al siguiente rellano."
            14 -> "¡Fiesta! Lanza un dado y todos los jugadores se dirigen a la planta del número del dado. Puede ser contrarrestada con 'Antisocial'."
            15 -> "Contrarresta 'Fiesta'. Te quedas en tu casilla y no vas a la fiesta."
            16 -> "Se fue la luz. Solo los jugadores con 'Linterna' podrán avanzar en su turno."
            17 -> "Linterna. Úsala después de 'A Ciegas' para poder seguir avanzando en tu turno."
            18 -> "¡Tropiezas! Caes al rellano inferior."
            19 -> "Elige un jugador: avanzará o retrocederá hasta tu casilla y se quedará un turno sin jugar."
            20 -> "Intercambia todas tus cartas con las de otro jugador."
            21 -> "Carta de noticias. No tiene ninguna acción especial."
            else -> "Descripción no disponible"
        }
    }

    fun initializeBoardSquares() {
        transaction {
            // Verificar si ya hay casillas
            val existingSquares = BoardSquares.selectAll().count()
            if (existingSquares > 0) {
                println("Las casillas del tablero ya están inicializadas")
                return@transaction
            }

            // Casillas especiales del tablero
            val specialSquares = mapOf(
                0 to Triple("ENTRANCE", 0, "Casilla Inicial - Planta 0"),
                8 to Triple("LANDING", null, "Rellano de Entre Planta"),
                16 to Triple("ELEVATOR_LANDING", 1, "Rellano Planta 1 - Puerta de Ascensor"),
                24 to Triple("LANDING", null, "Rellano de Entre Planta"),
                32 to Triple("ELEVATOR_LANDING", 2, "Rellano Planta 2 - Puerta de Ascensor"),
                40 to Triple("LANDING", null, "Rellano de Entre Planta"),
                48 to Triple("ELEVATOR_LANDING", 3, "Rellano Planta 3 - Puerta de Ascensor"),
                56 to Triple("LANDING", null, "Rellano de Entre Planta"),
                64 to Triple("ELEVATOR_LANDING", 4, "Rellano Planta 4 - Puerta de Ascensor"),
                72 to Triple("LANDING", null, "Rellano de Entre Planta"),
                80 to Triple("ELEVATOR_LANDING", 5, "Rellano Planta 5 - Puerta de Ascensor"),
                88 to Triple("LANDING", null, "Rellano de Entre Planta"),
                97 to Triple("ELEVATOR_LANDING", 6, "Rellano Planta 6 - Puerta de Ascensor"),
                105 to Triple("LANDING", null, "Rellano de Entre Planta"),
                112 to Triple("EXIT", null, "Puerta de Salida - ¡META!")
            )

            // Insertar todas las 113 casillas (0-112)
            for (position in 0..112) {
                val (type, floor, description) = specialSquares[position]
                    ?: Triple("NORMAL", null, "Casilla $position")

                BoardSquares.insert {
                    it[BoardSquares.position] = position
                    it[BoardSquares.type] = type
                    it[BoardSquares.floor] = floor
                    it[BoardSquares.description] = description
                }
            }

            println("113 casillas del tablero inicializadas correctamente (0-112)")
        }
    }
}