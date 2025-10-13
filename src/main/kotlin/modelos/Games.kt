package com.appsandgames34.modelos

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID

object Games : Table("games") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }

    val code = varchar("code", 10).uniqueIndex()
    val maxPlayers = integer("max_players")
    val status = varchar("status", 20) // WAITING, IN_PROGRESS, FINISHED
    val currentTurnIndex = integer("current_turn_index").default(0)
    val boardSize = integer("board_size").default(112)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at").nullable()
    val isStarted = bool("is_started").default(false)

    override val primaryKey = PrimaryKey(id)

}