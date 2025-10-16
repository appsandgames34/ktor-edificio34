package com.appsandgames34.modelos

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID

object UserSessions : Table("user_sessions") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val userId = uuid("user_id").references(
        Users.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.RESTRICT
    )
    val deviceId = varchar("device_id", 100) // Identificador único del dispositivo
    val token = text("token") // Token JWT activo
    val lastActivityAt = datetime("last_activity_at")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
