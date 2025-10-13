package com.appsandgames34.modelos
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID
object GameDecks : Table("game_decks") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val gameId = uuid("game_id").references(Games.id).uniqueIndex()
    val deckCards = text("deck_cards") // json array string
    val discard = text("discard").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)

}
