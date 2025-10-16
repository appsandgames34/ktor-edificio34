-- Migración para agregar columna is_public a tabla games
-- Ejecutar este script en la base de datos PostgreSQL

-- 1. Agregar columna is_public a games
ALTER TABLE games ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT false;

-- 2. Actualizar partidas existentes como privadas por defecto
UPDATE games SET is_public = false WHERE is_public IS NULL;

-- 3. Crear índice para mejorar búsquedas de partidas públicas
CREATE INDEX IF NOT EXISTS idx_games_is_public ON games(is_public);

-- 4. Crear tabla user_sessions si no existe
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(100) NOT NULL,
    token TEXT NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 5. Crear índice para búsquedas por user_id
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);

-- Verificar cambios
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'games'
ORDER BY ordinal_position;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
AND table_name = 'user_sessions';
