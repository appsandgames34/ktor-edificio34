package com.appsandgames34.modelos
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID

object BoardSquares : Table("board_squares") {
    val position = integer("position") // 0-112
    val type = varchar("type", 30) // NORMAL, ENTRANCE, LANDING, FLOOR_LANDING, ELEVATOR_LANDING
    val floor = integer("floor").nullable() // Número de planta (null para casillas normales)
    val description = varchar("description", 200)

    override val primaryKey = PrimaryKey(position)
}