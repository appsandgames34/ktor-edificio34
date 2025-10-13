package com.appsandgames34.modelos

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID
import javax.print.attribute.standard.RequestingUserName

data class User(
    val id: UUID,
    val username: String,
    val email: String
)

object Users : Table("users") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 200)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)

}
