package com.appsandgames34.modelos
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID
object Cards : Table("cards") {
    val id = integer("id")// 1..20
    val name = varchar("name", 100)
    val description = text("description")
    val properties = text("properties") // json string
    override val primaryKey = PrimaryKey(id)
}