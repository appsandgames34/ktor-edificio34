-- Script SQL para recrear manualmente la base de datos
-- Nota: Exposed crea las tablas automáticamente, esto es solo de respaldo

-- Crear base de datos
-- CREATE DATABASE edificio34;
-- \c edificio34;

-- Eliminar tablas si existen (en orden inverso por dependencias)
DROP TABLE IF EXISTS player_hands CASCADE;
DROP TABLE IF EXISTS game_decks CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS game_players CASCADE;
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

-- Tabla de Cartas
CREATE TABLE cards (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    properties TEXT NOT NULL
);

-- Tabla de Jugadores en Partidas
CREATE TABLE game_players (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    player_index INTEGER NOT NULL,
    character INTEGER NOT NULL CHECK (character BETWEEN 1 AND 6),
    position INTEGER NOT NULL DEFAULT 1,
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

-- Índices para mejorar rendimiento
CREATE INDEX idx_game_players_game_id ON game_players(game_id);
CREATE INDEX idx_game_players_user_id ON game_players(user_id);
CREATE INDEX idx_games_status ON games(status);
CREATE INDEX idx_games_code ON games(code);
CREATE INDEX idx_chat_messages_game_id ON chat_messages(game_id);
CREATE INDEX idx_player_hands_game_id ON player_hands(game_id);
CREATE INDEX idx_player_hands_player_id ON player_hands(player_id);

-- Insertar las 20 cartas del juego
INSERT INTO cards (id, name, description, properties) VALUES
(1, 'Avanzar +5', 'Avanza 5 casillas en el tablero. Perfecto para acercarte a la meta.', '{"effect": "advance", "value": 5}'),
(2, 'Avanzar +10', 'Avanza 10 casillas. Un gran salto hacia la victoria.', '{"effect": "advance", "value": 10}'),
(3, 'Retroceder -5', 'Retrocede 5 casillas. Úsala estratégicamente contra otros.', '{"effect": "retreat", "value": 5}'),
(4, 'Retroceder -10', 'Retrocede 10 casillas. Puede hacer que un oponente pierda terreno.', '{"effect": "retreat", "value": 10}'),
(5, 'Cambiar Posición', 'Intercambia tu posición con otro jugador. Elige sabiamente.', '{"effect": "swap_position", "type": "player"}'),
(6, 'Teletransporte', 'Teletranspórtate a una casilla aleatoria del tablero.', '{"effect": "teleport", "range": "random"}'),
(7, 'Escudo', 'Protégete de la próxima carta negativa que te jueguen.', '{"effect": "shield", "duration": 1}'),
(8, 'Congelar Jugador', 'Congela a un jugador durante su próximo turno.', '{"effect": "freeze", "duration": 1}'),
(9, 'Doble Turno', 'Juega dos turnos seguidos. Duplica tus posibilidades.', '{"effect": "extra_turn", "count": 1}'),
(10, 'Saltar Turno', 'Haz que otro jugador pierda su próximo turno.', '{"effect": "skip_turn", "target": "other"}'),
(11, 'Crash', 'Carta especial: Cancela cualquier carta que te hayan jugado. ¡Salvación instantánea!', '{"effect": "counter", "cancels": "any"}'),
(12, 'Robar Carta', 'Roba una carta aleatoria de la mano de otro jugador.', '{"effect": "steal_card", "target": "player"}'),
(13, 'Todos Atrás -3', 'Todos los jugadores retroceden 3 casillas excepto tú.', '{"effect": "all_retreat", "value": 3}'),
(14, 'Todos Adelante +3', 'Todos los jugadores avanzan 3 casillas. ¡Generosidad grupal!', '{"effect": "all_advance", "value": 3}'),
(15, 'Invertir Orden', 'Invierte el orden de los turnos del juego.', '{"effect": "reverse_order"}'),
(16, 'Llave Maestra', 'La Llave Maestra: Necesaria para ganar al llegar a la casilla 112.', '{"effect": "key", "required": true}'),
(17, 'Trampa', 'Coloca una trampa. El próximo jugador que pase retrocede 5 casillas.', '{"effect": "trap", "penalty": -5}'),
(18, 'Multiplicador x2', 'Multiplica por 2 el resultado de tus próximos dados.', '{"effect": "multiplier", "value": 2}'),
(19, 'Carta Comodín', 'Comodín: Puede copiar el efecto de cualquier carta en juego.', '{"effect": "wildcard", "flexible": true}'),
(20, 'Retorno al Inicio', 'Vuelve a la casilla de inicio. Úsala estratégicamente en otros.', '{"effect": "reset_position"}');

-- Verificar
SELECT COUNT(*) as total_cards FROM cards;
