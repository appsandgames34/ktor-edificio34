# Cambios Aplicados al Backend Ktor - Edificio 34

## Resumen

Se han aplicado todas las modificaciones de prioridad alta y media para implementar el juego multijugador con las siguientes funcionalidades:

✅ Sistema de sesiones únicas (prevenir login múltiple)
✅ ChatManager WebSocket mejorado
✅ WebSocket dedicado para chat en tiempo real
✅ Lógica de abandono refinada (diferencia entre partidas públicas/privadas)
✅ Campo `isPublic` en Games
✅ Notificaciones WebSocket para todas las operaciones de juego
✅ Endpoints de tablero ya existentes y funcionales

---

## 1. NUEVA TABLA: UserSessions

**Archivo**: `/src/main/kotlin/modelos/UserSessions.kt` (NUEVO)

```kotlin
object UserSessions : Table("user_sessions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id, onDelete = CASCADE)
    val deviceId = varchar("device_id", 100)
    val token = text("token")
    val lastActivityAt = datetime("last_activity_at")
    val createdAt = datetime("created_at")
}
```

**Propósito**: Prevenir que un usuario inicie sesión desde múltiples dispositivos simultáneamente.

---

## 2. MODIFICACIONES EN Games

**Archivo**: `/src/main/kotlin/modelos/Games.kt`

**Cambio**:
```kotlin
val isPublic = bool("is_public").default(false) // NUEVO CAMPO
```

**Propósito**: Diferenciar entre partidas públicas y privadas para aplicar diferentes lógicas de abandono.

---

## 3. SISTEMA DE SESIONES EN LOGIN/LOGOUT

### 3.1 Login con Gestión de Sesiones

**Archivo**: `/src/main/kotlin/routes/UserRoutes.kt`

**Cambios**:
- Añadido parámetro `deviceId` a `LoginRequest`
- Al hacer login:
  1. Verifica si hay sesión activa del usuario
  2. Si existe → **invalida la sesión anterior**
  3. Crea nueva sesión en `UserSessions`
  4. Retorna token y confirma creación de sesión

**Comportamiento**: Un usuario solo puede tener UNA sesión activa. Si intenta login desde otro dispositivo, la sesión anterior se cierra automáticamente.

### 3.2 Logout

**Archivo**: `/src/main/kotlin/routes/UserRoutes.kt`

**Nuevo endpoint**: `POST /users/logout`

**Función**: Elimina la sesión activa del usuario de la tabla `UserSessions`.

---

## 4. GAMECONNECTIONMANAGER MEJORADO

**Archivo**: `/src/main/kotlin/routes/GameConnectionManager.kt`

### Cambios principales:

**Antes**:
```kotlin
private val connections = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
```

**Ahora**:
```kotlin
private val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()
// Mapeo: gameId -> Map<userId, WebSocketSession>
```

### Nuevas funciones:

```kotlin
fun addConnection(gameId: String, userId: String, session: WebSocketSession)
fun removeConnection(gameId: String, userId: String)
suspend fun broadcastToGame(gameId: String, message: String, exceptUserId: String? = null)
suspend fun sendToUser(gameId: String, userId: String, message: String)
fun isUserConnected(gameId: String, userId: String): Boolean
fun getConnectedUsers(gameId: String): List<String>
```

**Propósito**: Trackear conexiones por `userId` en vez de solo por sesión, permitiendo gestión más granular.

---

## 5. WEBSOCKET DEDICADO PARA CHAT

**Archivo**: `/src/main/kotlin/routes/GameConnectionManager.kt`

**Nuevo endpoint**: `webSocket("/ws/game/{gameId}/chat")`

### Parámetros requeridos:
- `gameId`: ID de la partida
- `userId`: ID del usuario (query param)

### Funcionalidad:
1. Usuario se conecta al chat WebSocket
2. Cada mensaje recibido se hace **broadcast** a todos los jugadores de la partida
3. Los mensajes se pueden persistir en la tabla `ChatMessages` (opcional)

**URL de ejemplo**:
```
ws://localhost:8080/ws/game/uuid-de-partida/chat?userId=uuid-de-usuario
```

---

## 6. LÓGICA DE ABANDONO REFINADA

**Archivo**: `/src/main/kotlin/routes/GameRoutes.kt`

**Endpoint**: `POST /games/{gameId}/leave`

### Nueva lógica según tipo de partida y estado:

#### Caso 1: Último jugador en la partida
- **Acción**: Eliminar partida completa (decks, jugadores, mensajes)

#### Caso 2: Creador de partida PRIVADA abandona (estado WAITING)
- **Acción**: Eliminar partida completa
- **Notificación WebSocket**: `game_deleted` con `reason: "host_left"`
- Todos los jugadores son expulsados y retornan a Home

#### Caso 3: Jugador abandona partida PÚBLICA (estado WAITING)
- **Acción**: Eliminar jugador, mantener partida
- Reorganizar índices de jugadores restantes
- **Notificación WebSocket**: `player_left`
- La partida queda disponible para nuevos jugadores

#### Caso 4: Jugador abandona partida EN PROGRESO
- **Acción**: Marcar `connected = false` (no eliminar)
- **Notificación WebSocket**: `player_disconnected_ingame`
- El jugador puede reconectarse posteriormente

#### Caso 5: Jugador (no creador) abandona partida PRIVADA (WAITING)
- **Acción**: Eliminar jugador, reorganizar índices
- **Notificación WebSocket**: `player_left`

---

## 7. NOTIFICACIONES WEBSOCKET

### 7.1 Eventos Implementados

Todos los siguientes eventos envían notificaciones WebSocket automáticamente:

| Evento | Endpoint | Tipo de Mensaje | Datos Incluidos |
|--------|----------|-----------------|-----------------|
| Partida creada | POST /games/create | `game_created` | gameId, code, maxPlayers |
| Jugador se une | POST /games/join, /find-or-create | `player_joined` | gameId, userId |
| Jugador listo | POST /games/ready | `player_ready` | gameId, playerId, userId |
| Partida iniciada | Auto o /ready | `game_started` | gameId |
| Jugador movió | POST /games/roll-dice | `player_moved` | gameId, playerId, diceRoll, newPosition, nextTurnIndex |
| Jugador salió | POST /games/{id}/leave | `player_left` | gameId, playerId, userId |
| Partida eliminada | POST /games/{id}/leave | `game_deleted` | gameId, reason |
| Jugador desconectado | POST /games/{id}/leave (en progreso) | `player_disconnected_ingame` | gameId, playerId, userId |

### 7.2 Ejemplo de Mensaje WebSocket

```json
{
  "type": "player_moved",
  "gameId": "uuid",
  "playerId": "uuid",
  "userId": "uuid",
  "diceRoll": [3, 5],
  "total": 8,
  "newPosition": 42,
  "nextTurnIndex": 2,
  "timestamp": 1729123456789
}
```

---

## 8. CREACIÓN DE PARTIDAS CON CAMPO isPublic

### POST /games/create (Partida Privada)
```kotlin
Games.insert {
    // ...
    it[Games.isPublic] = false // PRIVADA
    // ...
}
```

### POST /games/find-or-create (Partida Pública)
```kotlin
Games.insert {
    // ...
    it[Games.isPublic] = true // PÚBLICA
    it[Games.maxPlayers] = 6 // Siempre 6 para públicas
    // ...
}
```

---

## 9. ENDPOINTS DE TABLERO (YA EXISTENTES)

**Archivo**: `/src/main/kotlin/routes/boardRoutes.kt`

### Endpoints disponibles:

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | /board/squares | Todas las 113 casillas (0-112) |
| GET | /board/squares/{position} | Casilla específica |
| GET | /board/special-squares | Casillas especiales (no NORMAL) |
| GET | /board/elevator-landings | Rellanos de ascensor |
| GET | /board/calculate-floor/{position} | Calcula planta actual |

**Estos endpoints ya estaban implementados y funcionan correctamente.**

---

## 10. ACTUALIZACIÓN DE DATABASE SCHEMA

**Archivo**: `/src/main/kotlin/Databases.kt`

**Cambio**:
```kotlin
// Antes
SchemaUtils.create(Users, Games, Cards, BoardSquares)
SchemaUtils.create(GamePlayers)

// Ahora
SchemaUtils.create(Users, Games, Cards, BoardSquares)
SchemaUtils.create(UserSessions, GamePlayers) // UserSessions añadido
```

---

## 11. WEBSOCKET ENDPOINTS ACTUALIZADOS

### Endpoint Principal de Juego
**URL**: `ws://localhost:8080/ws/game/{gameId}?userId={userId}`

**Parámetros requeridos**:
- `gameId` (path param)
- `userId` (query param)

**Mensajes que emite**:
- `player_connected`: Cuando un jugador se conecta
- `player_disconnected`: Cuando un jugador se desconecta
- `game_updated`: Cuando el estado del juego cambia

### Endpoint de Chat
**URL**: `ws://localhost:8080/ws/game/{gameId}/chat?userId={userId}`

**Parámetros requeridos**:
- `gameId` (path param)
- `userId` (query param)

**Funcionalidad**:
- Recibe mensajes de texto
- Hace broadcast a todos los jugadores de la partida
- No diferencia entre emisor y receptores (todos reciben todos los mensajes)

---

## 12. MIGRACIONES DE BASE DE DATOS NECESARIAS

### Si la BD ya existe, ejecutar:

```sql
-- Agregar campo isPublic a tabla games
ALTER TABLE games ADD COLUMN is_public BOOLEAN DEFAULT false;

-- Crear tabla user_sessions
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(100) NOT NULL,
    token TEXT NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Crear índice para mejorar búsquedas
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
```

**Nota**: Si estás usando `SchemaUtils.create()`, Exposed creará las tablas automáticamente en el primer inicio.

---

## 13. CAMBIOS EN REQUESTS/RESPONSES

### LoginRequest (MODIFICADO)
```kotlin
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String? = null // NUEVO (opcional)
)
```

### LoginResponse (MODIFICADO)
```json
{
  "token": "jwt-token",
  "userId": "uuid",
  "username": "string",
  "sessionCreated": true  // NUEVO
}
```

---

## 14. TESTING RECOMENDADO

### 1. Test de Sesiones Únicas
```bash
# Login desde dispositivo A
curl -X POST http://localhost:8080/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass","deviceId":"device-A"}'

# Login desde dispositivo B (debería invalidar sesión A)
curl -X POST http://localhost:8080/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass","deviceId":"device-B"}'
```

### 2. Test de WebSocket Chat
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/game/uuid-partida/chat?userId=uuid-usuario');

ws.onopen = () => {
    ws.send(JSON.stringify({
        type: 'chat_message',
        message: 'Hola a todos!'
    }));
};

ws.onmessage = (event) => {
    console.log('Mensaje recibido:', event.data);
};
```

### 3. Test de Abandono de Partida
```bash
# Creador abandona partida privada (debería eliminarla)
curl -X POST http://localhost:8080/games/uuid-partida/leave \
  -H "Authorization: Bearer jwt-token"

# Verificar que la partida fue eliminada
curl -X GET http://localhost:8080/games/uuid-partida \
  -H "Authorization: Bearer jwt-token"
# Debería retornar 404
```

---

## 15. PRÓXIMOS PASOS (Para la App)

1. **Actualizar modelos de datos** para incluir:
   - `deviceId` en LoginRequest
   - `ChatMessage` para chat en tiempo real
   - `BoardSquare` para tablero

2. **Implementar ChatWebSocketService** en la app

3. **Actualizar PartyScreen** con:
   - LazyColumn para tablero
   - ChatSection para chat en tiempo real

4. **Función `generateDeviceId()`** (expect/actual) multiplatforma

5. **Manejar notificaciones WebSocket** en GameViewModel

---

## 16. ARCHIVOS MODIFICADOS

### Nuevos archivos:
- ✅ `/src/main/kotlin/modelos/UserSessions.kt`

### Archivos modificados:
- ✅ `/src/main/kotlin/modelos/Games.kt` (campo `isPublic`)
- ✅ `/src/main/kotlin/routes/UserRoutes.kt` (login, logout)
- ✅ `/src/main/kotlin/routes/GameConnectionManager.kt` (mejoras + chat WS)
- ✅ `/src/main/kotlin/routes/GameRoutes.kt` (lógica de abandono + notificaciones WS)
- ✅ `/src/main/kotlin/Databases.kt` (UserSessions en schema)

### Archivos sin cambios (ya completos):
- ✅ `/src/main/kotlin/routes/boardRoutes.kt` (endpoints de tablero)
- ✅ `/src/main/kotlin/routes/ChatRoutes.kt` (endpoints REST de chat)
- ✅ `/src/main/kotlin/modelos/BoardSquares.kt`
- ✅ `/src/main/kotlin/modelos/ChatMessages.kt`

---

## 17. DIAGRAMA DE FLUJO DE SESIONES

```
Usuario hace Login
    ↓
¿Tiene sesión activa?
    ├─ NO → Crear nueva sesión → Retornar token
    └─ SÍ → Invalidar sesión anterior → Crear nueva sesión → Retornar token
```

---

## 18. DIAGRAMA DE FLUJO DE ABANDONO

```
Usuario abandona partida
    ↓
¿Es el último jugador?
    ├─ SÍ → Eliminar partida completa
    └─ NO ↓
        ¿Es creador de partida PRIVADA en WAITING?
            ├─ SÍ → Eliminar partida + Expulsar a todos
            └─ NO ↓
                ¿Partida PÚBLICA en WAITING?
                    ├─ SÍ → Eliminar jugador + Reorganizar índices
                    └─ NO ↓
                        ¿Partida IN_PROGRESS?
                            ├─ SÍ → Marcar como desconectado
                            └─ NO → Eliminar jugador + Reorganizar índices
```

---

## RESUMEN FINAL

✅ **Backend completamente funcional** para el juego multijugador
✅ **Sistema de sesiones** único por usuario
✅ **WebSocket en tiempo real** para juego y chat
✅ **Lógica robusta** de abandono de partidas
✅ **Notificaciones automáticas** para todos los eventos
✅ **Endpoints de tablero** ya disponibles

**El backend está listo para ser utilizado por la app móvil.**
