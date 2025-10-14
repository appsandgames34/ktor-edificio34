package com.appsandgames34.modelos

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID

// Tabla para efectos activos en jugadores
object PlayerEffects : Table("player_effects") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val playerId = uuid("player_id").references(GamePlayers.id)
    val gameId = uuid("game_id").references(Games.id)
    val effectType = varchar("effect_type", 50) // SHIELD, FROZEN, DOUBLE_TURN, FLASHLIGHT, etc
    val turnsRemaining = integer("turns_remaining").default(1)
    val metadata = text("metadata").nullable() // JSON para datos adicionales
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}