package com.appsandgames34.util

import java.util.UUID

data class CreateGameRequest(val maxPlayers: Int, val isPublic: Boolean = true)
data class JoinGameRequest(val code: String?) // si code null -> matchmaking publico
data class GameSummary(val id: UUID, val code: String, val status: String, val players: Int, val maxPlayers: Int)
data class PlayerActionDto(val actionType: String, val payload: Map<String, Any?>) // e.g. play_card, roll_dice
data class ChatMessageDto(val gameId: UUID, val playerId: UUID?, val message: String)
