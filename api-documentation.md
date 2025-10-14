# Edificio 34 - Documentación de API

## Descripción del Juego

Juego de tablero multijugador (2-6 jugadores) con:
- **Tablero**: 112 casillas
- **Objetivo**: Llegar a la casilla 112 con la Llave Maestra (carta #16)
- **Mecánicas**: Tirar dados (2d6) para avanzar y jugar cartas estratégicas
- **Cartas**: 20 tipos diferentes, cada jugador tiene 3 en su mano
- **Personajes**: 6 personajes asignados aleatoriamente

---

## Autenticación

Todas las rutas protegidas requieren un token JWT en el header:
```
Authorization: Bearer <token>
```

---

## Endpoints

### 1. USUARIOS (`/users`)

#### **POST** `/users/register`
Registrar un nuevo usuario.

**Request Body:**
```json
{
  "username": "jugador1",
  "email": "jugador1@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "uuid-del-usuario",
  "username": "jugador1"
}
```

---

#### **POST** `/users/login`
Iniciar sesión.

**Request Body:**
```json
{
  "username": "jugador1",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "uuid-del-usuario",
  "username": "jugador1"
}
```

---

#### **GET** `/users/profile` 🔒
Obtener perfil del usuario actual.

**Headers:** `Authorization: Bearer <token>`

**Response:**
```json
{
  "id": "uuid-del-usuario",
  "username": "jugador1",
  "email": "jugador1@example.com",
  "createdAt": "2025-10-14T12:00:00"
}
```

---

### 2. PARTIDAS (`/games`)

#### **POST** `/games/create` 🔒
Crear una partida privada con código.

**Request Body:**
```json
{
  "maxPlayers": 4,
  "isPublic": false
}
```

**Response:**
```json
{
  "gameId": "uuid-de-la-partida",
  "code": "A3B5C7"
}
```

---

#### **POST** `/games/find-or-create` 🔒
Buscar partida pública disponible o crear una nueva automáticamente.

**Request Body:** Vacío o `{}`

**Response:**
```json
{
  "gameId": "uuid-de-la-partida",
  "code": "X9Y2Z4",
  "created": false
}
```

`created`: `true` si se creó nueva partida, `false` si se unió a una existente.

---

#### **POST** `/games/join` 🔒
Unirse a una partida usando el código.

**Request Body:**
```json
{
  "code": "A3B5C7"
}
```

**Response:**
```
"Te has unido a la partida"
```

---

#### **POST** `/games/ready` 🔒
Marcar que estás listo para comenzar. Cuando todos están listos, la partida comienza automáticamente.

**Request Body:**
```json
{
  "gameId": "uuid-de-la-partida"
}
```

**Response:**
```
"Listo para jugar"
```

---

#### **GET** `/games/{gameId}` 🔒
Obtener estado completo de la partida.

**Response:**
```json
{
  "id": "uuid-de-la-partida",
  "code": "A3B5C7",
  "maxPlayers": 4,
  "status": "IN_PROGRESS",
  "isStarted": true,
  "currentTurnIndex": 2,
  "boardSize": 112,
  "players": [
    {
      "playerId": "uuid-player-1",
      "userId": "uuid-user-1",
      "username": "jugador1",
      "character": 3,
      "position": 25,
      "isReady": true,
      "connected": true,
      "playerIndex": 0
    }
  ]
}
```

---

#### **GET** `/games/available` 🔒
Listar partidas públicas disponibles.

**Response:**
```json
[
  {
    "id": "uuid-partida-1",
    "code": "ABC123",
    "maxPlayers": 6,
    "currentPlayers": 3
  }
]
```

---

#### **POST** `/games/roll-dice` 🔒
Tirar los dados para avanzar (solo en tu turno).

**Request Body:**
```json
{
  "gameId": "uuid-de-la-partida"
}
```

**Response:**
```json
{
  "dice1": 4,
  "dice2": 6,
  "total": 10,
  "newPosition": 35
}
```

---

#### **GET** `/games/{gameId}/hand` 🔒
Ver tus cartas actuales.

**Response:**
```json
{
  "cards": [1, 5, 11]
}
```

---

### 3. CARTAS (`/cards`)

#### **POST** `/cards/play` 🔒
Jugar una carta de tu mano.

**Request Body:**
```json
{
  "gameId": "uuid-de-la-partida",
  "cardId": 5,
  "targetPlayerId": "uuid-del-jugador-objetivo"
}
```

`targetPlayerId` es opcional, solo se usa para cartas que requieren objetivo.

**Response:**
```json
{
  "success": true,
  "cardId": 5,
  "cardName": "Cambiar Posición",
  "cardsInHand": 2
}
```

---

#### **POST** `/cards/draw` 🔒
Robar una carta del mazo (después de jugar una).

**Request Body:**
```json
{
  "gameId": "uuid-de-la-partida"
}
```

**Response:**
```json
{
  "cardId": 8,
  "cardsInHand": 3,
  "cardsInDeck": 45
}
```

---

#### **GET** `/cards/info`
Obtener información de todas las cartas del juego.

**Response:**
```json
[
  {
    "id": 1,
    "name": "Avanzar +5",
    "description": "Avanza 5 casillas en el tablero...",
    "properties": "{\"effect\": \"advance\", \"value\": 5}"
  }
]
```

---

#### **GET** `/cards/info/{cardId}`
Obtener información de una carta específica.

**Response:**
```json
{
  "id": 11,
  "name": "Crash",
  "description": "Carta especial: Cancela cualquier carta...",
  "properties": "{\"effect\": \"counter\", \"cancels\": \"any\"}"
}
```

---

### 4. CHAT (`/chat`)

#### **POST** `/chat/send` 🔒
Enviar mensaje al chat de la partida.

**Request Body:**
```json
{
  "gameId": "uuid-de-la-partida",
  "playerId": "uuid-del-jugador",
  "message": "¡Buena jugada!"
}
```

`playerId` puede ser `null` para mensajes del sistema.

**Response:**
```
"Mensaje enviado"
```

---

#### **GET** `/chat/messages/{gameId}` 🔒
Obtener historial de mensajes de la partida.

**Response:**
```json
[
  {
    "playerId": "uuid-del-jugador",
    "message": "¡Hola a todos!",
    "createdAt": "2025-10-14T12:30:00"
  }
]
```

---

### 5. WEBSOCKET

#### **WS** `/ws/game/{gameId}`
Conexión WebSocket para actualizaciones en tiempo real.

**Conexión:**
```javascript
const ws = new WebSocket("ws://localhost:8080/ws/game/{gameId}");
```

**Mensajes recibidos:**

**Jugador conectado:**
```json
{
  "type": "player_connected",
  "gameId": "uuid-de-la-partida",
  "timestamp": 1697280000000
}
```

**Jugador desconectado:**
```json
{
  "type": "player_disconnected",
  "gameId": "uuid-de-la-partida",
  "timestamp": 1697280000000
}
```

**Carta jugada:**
```json
{
  "type": "card_played",
  "userId": "uuid-del-usuario",
  "cardId": 5,
  "timestamp": 1697280000000
}
```

---

## Lista de Cartas (21 tipos)

| ID | Nombre | Efecto | Tipo |
|----|--------|--------|------|
| 1 | **Llave** | Necesaria para ganar en casilla 112 | Objetivo |
| 2 | Especial Alarma | Todos a casilla 0 (menos tú) | Especial |
| 3 | Te Pillé | Crash de Alarma - Te quedas en tu sitio | Defensa |
| 4 | Zapatillas Aladas | Dados x3 | Movimiento |
| 5 | Tijeretazo | Crash Zapatillas - Solo 1 dado x3 | Defensa |
| 6 | Recién Fregado | Bloquea casilla 1 ronda | Obstáculo |
| 7 | Tirada Doble | Tira dados 2 veces | Movimiento |
| 8 | Entrega de Paquete | Otro baja 2 plantas | Ataque |
| 9 | Crossfitter | Solo 1 dado este turno | Limitación |
| 10 | Subiendo | Sube 1 planta desde rellano | Movimiento |
| 11 | Vecino Maravilloso | Crash Subiendo - Cancela subida | Defensa |
| 12 | Especial Cuarentena | Fin de partida, nadie gana | Especial |
| 13 | Gatito que Ronronea | Coloca gato trampa en casilla | Trampa |
| 14 | Fiesta | Todos van a planta aleatoria | Movimiento |
| 15 | Antisocial | Crash Fiesta - Te quedas en tu sitio | Defensa |
| 16 | A Ciegas | Solo pueden moverse con Linterna | Limitación |
| 17 | Linterna | Crash A Ciegas - Permite moverte | Defensa |
| 18 | Catapún | Caes al rellano inferior | Movimiento |
| 19 | Chisme | Trae jugador a tu casilla, pierde turno | Ataque |
| 20 | Intercambio | Intercambia todas tus cartas | Especial |
| 21 | News | Sin efecto | Neutral |

---

## Tablero del Edificio

### Estructura General
- **Total**: 113 casillas (0-112)
- **Plantas**: 7 plantas (0-6)
- **Meta**: Casilla 112 (Puerta de Salida)

### Casillas Especiales

#### Casilla Inicial
- **Casilla 0**: Punto de inicio - Planta 0

#### Rellanos de Entre Planta
- **Casilla 8**: Rellano entre plantas
- **Casilla 24**: Rellano entre plantas
- **Casilla 40**: Rellano entre plantas
- **Casilla 56**: Rellano entre plantas
- **Casilla 72**: Rellano entre plantas
- **Casilla 88**: Rellano entre plantas
- **Casilla 105**: Rellano entre plantas

#### Rellanos con Puerta de Ascensor (Uso de carta "Subiendo")
- **Casilla 16**: Planta 1 - Puerta de Ascensor
- **Casilla 32**: Planta 2 - Puerta de Ascensor
- **Casilla 48**: Planta 3 - Puerta de Ascensor
- **Casilla 64**: Planta 4 - Puerta de Ascensor
- **Casilla 80**: Planta 5 - Puerta de Ascensor
- **Casilla 97**: Planta 6 - Puerta de Ascensor

#### Meta
- **Casilla 112**: Puerta de Salida (requiere carta **Llave** #1)

### Distribución por Plantas
- **Planta 0**: Casillas 0-15 (inicio)
- **Planta 1**: Casillas 16-31
- **Planta 2**: Casillas 32-47
- **Planta 3**: Casillas 48-63
- **Planta 4**: Casillas 64-79
- **Planta 5**: Casillas 80-96
- **Planta 6**: Casillas 97-112 (meta)

---

## Nuevos Endpoints - Tablero

### **GET** `/board/squares`
Obtener todas las casillas del tablero.

**Response:**
```json
[
  {
    "position": 0,
    "type": "ENTRANCE",
    "floor": 0,
    "description": "Casilla Inicial - Planta 0"
  },
  {
    "position": 16,
    "type": "ELEVATOR_LANDING",
    "floor": 1,
    "description": "Rellano Planta 1 - Puerta de Ascensor"
  }
]
```

---

### **GET** `/board/squares/{position}`
Obtener información de una casilla específica.

**Response:**
```json
{
  "position": 16,
  "type": "ELEVATOR_LANDING",
  "floor": 1,
  "description": "Rellano Planta 1 - Puerta de Ascensor"
}
```

---

### **GET** `/board/special-squares`
Obtener solo las casillas especiales (rellanos, ascensores, meta).

**Response:**
```json
[
  {
    "position": 0,
    "type": "ENTRANCE",
    "floor": 0,
    "description": "Casilla Inicial - Planta 0"
  }
]
```

---

### **GET** `/board/elevator-landings`
Obtener todos los rellanos con puerta de ascensor.

**Response:**
```json
[
  {
    "position": 16,
    "floor": 1,
    "description": "Rellano Planta 1 - Puerta de Ascensor"
  }
]
```

---

### **GET** `/board/calculate-floor/{position}`
Calcular planta y características de una posición.

**Response:**
```json
{
  "position": 16,
  "floor": 1,
  "isElevatorLanding": true,
  "isLanding": false,
  "isExit": false
}
```

---

## Flujo del Juego

### 1. Preparación
1. Registrarse/Login → Obtener JWT token
2. Buscar partida pública o crear privada
3. Esperar a que todos los jugadores estén listos
4. Cada jugador recibe 3 cartas al iniciar

### 2. Turno del Jugador
1. **Fase de Carta** (Opcional): Jugar una carta de tu mano
   - Otros jugadores pueden usar "Crash" (#11) para contrarrestar
2. **Fase de Dados**: Tirar 2 dados y avanzar
3. **Fase de Robo**: Si jugaste carta, robar una nueva
4. Turno pasa al siguiente jugador

### 3. Victoria
Llegar a la casilla 112 teniendo la **Llave Maestra** (carta #16) en tu mano o jugándola.

---

## Estados de Partida

- `WAITING`: Esperando jugadores
- `IN_PROGRESS`: Partida en curso
- `FINISHED`: Partida terminada

---

## Códigos de Error HTTP

- `200 OK`: Éxito
- `400 Bad Request`: Error en la petición o validación
- `401 Unauthorized`: Token inválido o ausente
- `404 Not Found`: Recurso no encontrado
- `409 Conflict`: Conflicto (ej: usuario ya existe)
- `500 Internal Server Error`: Error del servidor

---

## Configuración del Servidor

**Archivo:** `src/main/resources/application.yaml`

```yaml
ktor:
  deployment:
    port: 8080
  application:
    modules:
      - com.appsandgames34.ApplicationKt.module

postgres:
  url: "jdbc:postgresql://localhost:5432/edificio34"
  user: "postgres"
  password: "tu_contraseña"
```

---

## Iniciar el Servidor

```bash
# Desarrollo
./gradlew run

# Construir JAR
./gradlew buildFatJar

# Ejecutar JAR
java -jar build/libs/ktor-edificio34-all.jar
```

---

## Base de Datos PostgreSQL

Crear la base de datos:
```sql
CREATE DATABASE edificio34;
```

Las tablas se crean automáticamente al iniciar el servidor.

---

## Consideraciones para KMP (Cliente)

### Librerías Recomendadas

**Networking:**
- Ktor Client (multiplataforma)
- kotlinx.serialization

**WebSocket:**
- Ktor Client WebSocket

**Ejemplo de cliente:**
```kotlin
// Setup Ktor Client
val client = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(Auth) {
        bearer {
            loadTokens {
                BearerTokens(token, token)
            }
        }
    }
}

// Login
val response = client.post("http://localhost:8080/users/login") {
    contentType(ContentType.Application.Json)
    setBody(LoginRequest("usuario", "password"))
}

// WebSocket
val session = client.webSocketSession("ws://localhost:8080/ws/game/$gameId")
```

---

## Próximas Mejoras

- [ ] Sistema de rankings y estadísticas
- [ ] Replay de partidas
- [ ] Torneos y ligas
- [ ] Sistema de amigos
- [ ] Notificaciones push
- [ ] Múltiples tableros temáticos
- [ ] Más tipos de cartas
- [ ] Sistema de logros
- [ ] Partidas ranqueadas

---

## Soporte

Para problemas o preguntas sobre la API, consulta el código fuente o abre un issue en el repositorio.