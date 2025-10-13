package com.appsandgames34.modelos
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID
object ChatMessages : Table("chat_messages") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val gameId = uuid("game_id").references(Games.id)
    val playerId = uuid("player_id").references(GamePlayers.id).nullable()
    val message = text("message")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
