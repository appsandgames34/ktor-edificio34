package com.appsandgames34.modelos
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID
object PlayerHands : Table("player_hands") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val gameId = uuid("game_id").references(Games.id)
    val playerId = uuid("player_id").references(GamePlayers.id)
    val cards = text("cards") // json array string with up to 3 integers

    override val primaryKey = PrimaryKey(id)
}