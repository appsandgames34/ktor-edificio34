package com.appsandgames34.modelos
import org.jetbrains.exposed.sql.ReferenceOption // Import this!
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.util.*

object GamePlayers : Table("game_players") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }

    val gameId = uuid("game_id").references(
        Games.id,
        onDelete = ReferenceOption.RESTRICT,
        onUpdate = ReferenceOption.RESTRICT
    )

    // Assumes all players must be registered users (non-nullable)
    val userId = uuid("user_id").references(
        Users.id,
        onDelete = ReferenceOption.RESTRICT,
        onUpdate = ReferenceOption.RESTRICT
    )


    val playerIndex = integer("player_index")
    val character = integer("character") // 1..6
    val position = integer("position").default(1)
    val isReady = bool("is_ready").default(false)
    val connected = bool("connected").default(false)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

}