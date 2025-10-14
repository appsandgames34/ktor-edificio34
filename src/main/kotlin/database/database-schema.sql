-- Script SQL para recrear manualmente la base de datos
-- Nota: Exposed crea las tablas automáticamente, esto es solo de respaldo

-- Crear base de datos
-- CREATE DATABASE edificio34;
-- \c edificio34;

-- Eliminar tablas si existen (en orden inverso por dependencias)
DROP TABLE IF EXISTS player_effects CASCADE;
DROP TABLE IF EXISTS player_hands CASCADE;
DROP TABLE IF EXISTS game_decks CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS game_players CASCADE;
DROP TABLE IF EXISTS board_squares CASCADE;
DROP TABLE IF EXISTS games CASCADE;
DROP TABLE IF EXISTS cards CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Tabla de Usuarios
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Tabla de Partidas
CREATE TABLE games (
    id UUID PRIMARY KEY,
    code VARCHAR(10) UNIQUE NOT NULL,
    max_players INTEGER NOT NULL CHECK (max_players BETWEEN 2 AND 6),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    current_turn_index INTEGER NOT NULL DEFAULT 0,
    board_size INTEGER NOT NULL DEFAULT 112,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    is_started BOOLEAN NOT NULL DEFAULT FALSE
);

-- Tabla de Cartas (21 tipos)
CREATE TABLE cards (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    properties TEXT NOT NULL
);

-- Tabla de Casillas del Tablero (0-112)
CREATE TABLE board_squares (
    position INTEGER PRIMARY KEY CHECK (position BETWEEN 0 AND 112),
    type VARCHAR(30) NOT NULL,
    floor INTEGER,
    description VARCHAR(200) NOT NULL
);

-- Tabla de Jugadores en Partidas
CREATE TABLE game_players (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    player_index INTEGER NOT NULL,
    character INTEGER NOT NULL CHECK (character BETWEEN 1 AND 6),
    position INTEGER NOT NULL DEFAULT 0,
    is_ready BOOLEAN NOT NULL DEFAULT FALSE,
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(game_id, user_id),
    UNIQUE(game_id, player_index)
);

-- Tabla de Mazos de Partidas
CREATE TABLE game_decks (
    id UUID PRIMARY KEY,
    game_id UUID UNIQUE NOT NULL REFERENCES games(id),
    deck_cards TEXT NOT NULL,
    discard TEXT,
    created_at TIMESTAMP NOT NULL
);

-- Tabla de Manos de Jugadores
CREATE TABLE player_hands (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES games(id),
    player_id UUID NOT NULL REFERENCES game_players(id),
    cards TEXT NOT NULL,
    UNIQUE(game_id, player_id)
);

-- Tabla de Mensajes de Chat
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES games(id),
    player_id UUID REFERENCES game_players(id),
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Tabla de Efectos Activos en Jugadores
CREATE TABLE player_effects (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES game_players(id),
    game_id UUID NOT NULL REFERENCES games(id),
    effect_type VARCHAR(50) NOT NULL,
    turns_remaining INTEGER NOT NULL DEFAULT 1,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL
);

-- Índices para mejorar rendimiento
CREATE INDEX idx_game_players_game_id ON game_players(game_id);
CREATE INDEX idx_game_players_user_id ON game_players(user_id);
CREATE INDEX idx_games_status ON games(status);
CREATE INDEX idx_games_code ON games(code);
CREATE INDEX idx_chat_messages_game_id ON chat_messages(game_id);
CREATE INDEX idx_player_hands_game_id ON player_hands(game_id);
CREATE INDEX idx_player_hands_player_id ON player_hands(player_id);
CREATE INDEX idx_player_effects_player_id ON player_effects(player_id);
CREATE INDEX idx_player_effects_game_id ON player_effects(game_id);

-- Insertar las 21 cartas del juego
INSERT INTO cards (id, name, description, properties) VALUES
(1, 'Llave', 'Carta necesaria para ganar. Debes tener esta carta y llegar a la casilla 112 (Puerta de Salida).', '{"effect": "key", "required": true, "isSpecial": false}'),
(2, 'Especial Alarma', '¡ALARMA! Todos los jugadores van a la casilla de inicio (casilla 0) excepto tú. Puede ser contrarrestada con ''Te Pillé''.', '{"effect": "alarm_all_to_start", "excludeOwner": true, "isSpecial": true, "canBeCrashed": true, "crashedBy": [3]}'),
(3, 'Te Pillé (Crash Alarma)', 'Carta de defensa. Úsala cuando alguien juegue ''Especial Alarma'' para quedarte en tu sitio y no bajar.', '{"effect": "crash_alarm", "crashTarget": 2, "isSpecial": false}'),
(4, 'Zapatillas Aladas', 'Multiplica tu tirada de dados por 3. ¡Avanza muy rápido! Puede ser contrarrestada con ''Tijeretazo''.', '{"effect": "multiply_dice", "multiplier": 3, "isSpecial": false, "canBeCrashed": true, "crashedBy": [5]}'),
(5, 'Tijeretazo (Crash Zapatillas)', 'Contrarresta ''Zapatillas Aladas''. El rival solo usará un dado multiplicado por 3.', '{"effect": "crash_winged_shoes", "crashTarget": 4, "reduceTo": "single_dice_x3", "isSpecial": false}'),
(6, 'Recién Fregado', 'Bloquea una casilla del tablero. Ningún jugador puede pasar por ella durante una ronda.', '{"effect": "block_square", "duration": 1, "isSpecial": false}'),
(7, 'Tirada Doble', '¡Tirada doble! Este turno tiras los dados 2 veces.', '{"effect": "double_roll", "count": 2, "isSpecial": false}'),
(8, 'Entrega de Paquete', 'Elige a un jugador para que baje 2 plantas a entregar un paquete.', '{"effect": "go_down_floors", "floors": 2, "targetOther": true, "isSpecial": false}'),
(9, 'Crossfitter', 'Este turno solo tiras con un dado. Úsala estratégicamente.', '{"effect": "single_dice", "isSpecial": false}'),
(10, 'Subiendo', 'Si estás en el rellano de una planta, sube a la siguiente planta y continúa tu turno. Puede ser contrarrestada con ''Vecino Maravilloso''.', '{"effect": "go_up_floor", "requiresFloorLanding": true, "isSpecial": false, "canBeCrashed": true, "crashedBy": [11]}'),
(11, 'Vecino Maravilloso (Crash Subiendo)', 'Contrarresta ''Subiendo''. Cancela la subida de planta del rival.', '{"effect": "crash_go_up", "crashTarget": 10, "isSpecial": false}'),
(12, 'Especial Cuarentena', '¡CUARENTENA! El edificio entra en cuarentena, la partida se acaba y nadie gana.', '{"effect": "quarantine_end_game", "endGame": true, "noWinner": true, "isSpecial": true}'),
(13, 'Gatito que Ronronea', 'Coloca un gato en la casilla que quieras. El primero que pase lanzará un dado: 1-3 baja al rellano inferior, 4-6 sube al siguiente rellano.', '{"effect": "place_cat", "diceRoll": true, "lowRoll": "down", "highRoll": "up", "isSpecial": false}'),
(14, 'Fiesta', '¡Fiesta! Lanza un dado y todos los jugadores se dirigen a la planta del número del dado. Puede ser contrarrestada con ''Antisocial''.', '{"effect": "all_to_floor", "diceRoll": true, "isSpecial": false, "canBeCrashed": true, "crashedBy": [15]}'),
(15, 'Antisocial (Crash Fiesta)', 'Contrarresta ''Fiesta''. Te quedas en tu casilla y no vas a la fiesta.', '{"effect": "crash_party", "crashTarget": 14, "stayInPlace": true, "isSpecial": false}'),
(16, 'A Ciegas', 'Se fue la luz. Solo los jugadores con ''Linterna'' podrán avanzar en su turno.', '{"effect": "blackout", "requiresFlashlight": true, "isSpecial": false}'),
(17, 'Linterna (Crash A Ciegas)', 'Linterna. Úsala después de ''A Ciegas'' para poder seguir avanzando en tu turno.', '{"effect": "flashlight", "crashTarget": 16, "allowMovement": true, "isSpecial": false}'),
(18, 'Catapún', '¡Tropiezas! Caes al rellano inferior.', '{"effect": "fall_down_landing", "isSpecial": false}'),
(19, 'Chisme', 'Elige un jugador: avanzará o retrocederá hasta tu casilla y se quedará un turno sin jugar.', '{"effect": "gossip", "pullToPosition": true, "skipTurn": true, "targetOther": true, "isSpecial": false}'),
(20, 'Intercambio', 'Intercambia todas tus cartas con las de otro jugador.', '{"effect": "swap_hands", "targetOther": true, "isSpecial": false}'),
(21, 'News', 'Carta de noticias. No tiene ninguna acción especial.', '{"effect": "none", "isSpecial": false}');

-- Insertar las 113 casillas del tablero (0-112)
-- Casillas especiales
INSERT INTO board_squares (position, type, floor, description) VALUES
(0, 'ENTRANCE', 0, 'Casilla Inicial - Planta 0'),
(8, 'LANDING', NULL, 'Rellano de Entre Planta'),
(16, 'ELEVATOR_LANDING', 1, 'Rellano Planta 1 - Puerta de Ascensor'),
(24, 'LANDING', NULL, 'Rellano de Entre Planta'),
(32, 'ELEVATOR_LANDING', 2, 'Rellano Planta 2 - Puerta de Ascensor'),
(40, 'LANDING', NULL, 'Rellano de Entre Planta'),
(48, 'ELEVATOR_LANDING', 3, 'Rellano Planta 3 - Puerta de Ascensor'),
(56, 'LANDING', NULL, 'Rellano de Entre Planta'),
(64, 'ELEVATOR_LANDING', 4, 'Rellano Planta 4 - Puerta de Ascensor'),
(72, 'LANDING', NULL, 'Rellano de Entre Planta'),
(80, 'ELEVATOR_LANDING', 5, 'Rellano Planta 5 - Puerta de Ascensor'),
(88, 'LANDING', NULL, 'Rellano de Entre Planta'),
(97, 'ELEVATOR_LANDING', 6, 'Rellano Planta 6 - Puerta de Ascensor'),
(105, 'LANDING', NULL, 'Rellano de Entre Planta'),
(112, 'EXIT', NULL, 'Puerta de Salida - ¡META!');

-- Insertar casillas normales (todas las demás)
DO $
BEGIN
    FOR i IN 0..112 LOOP
        IF NOT EXISTS (SELECT 1 FROM board_squares WHERE position = i) THEN
            INSERT INTO board_squares (position, type, floor, description)
            VALUES (i, 'NORMAL', NULL, 'Casilla ' || i);
        END IF;
    END LOOP;
END $;

-- Verificar
SELECT COUNT(*) as total_cards FROM cards;
SELECT COUNT(*) as total_squares FROM board_squares;