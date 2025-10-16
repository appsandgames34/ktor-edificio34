# Instrucciones de Migración - Base de Datos

## ⚠️ IMPORTANTE: Ejecutar ANTES de usar el servidor actualizado

La base de datos existente necesita ser actualizada con los nuevos cambios. Sigue estos pasos:

## Opción 1: Ejecutar script SQL (RECOMENDADO)

### Paso 1: Conectar a PostgreSQL

```bash
# En tu terminal, conecta a PostgreSQL
psql -U tu_usuario -d nombre_base_datos
```

O si usas pgAdmin, conecta a tu base de datos desde la GUI.

### Paso 2: Ejecutar el script de migración

```bash
# Desde la terminal de PostgreSQL
\i /tmp/ktor-edificio34/migration_add_is_public.sql
```

O si usas pgAdmin:
1. Abre el archivo `migration_add_is_public.sql`
2. Copia todo el contenido
3. Pégalo en la ventana de Query Tool
4. Ejecuta (botón de play o F5)

### Paso 3: Verificar cambios

Deberías ver confirmaciones:
- `ALTER TABLE`
- `UPDATE X rows`
- `CREATE INDEX`
- `CREATE TABLE`

## Opción 2: Ejecutar manualmente (línea por línea)

Si prefieres ejecutar los comandos uno por uno:

```sql
-- 1. Agregar columna is_public
ALTER TABLE games ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT false;

-- 2. Actualizar partidas existentes
UPDATE games SET is_public = false WHERE is_public IS NULL;

-- 3. Crear índice
CREATE INDEX IF NOT EXISTS idx_games_is_public ON games(is_public);

-- 4. Crear tabla user_sessions
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(100) NOT NULL,
    token TEXT NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 5. Crear índice para user_sessions
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
```

## Opción 3: Recrear Base de Datos (si tienes datos de prueba)

Si estás en desarrollo y puedes perder los datos:

```sql
-- CUIDADO: Esto eliminará TODOS los datos
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- Luego reinicia el servidor Ktor y las tablas se crearán automáticamente
```

## Verificación de Migración Exitosa

Después de ejecutar la migración, verifica:

```sql
-- Verificar columna is_public en games
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_name = 'games' AND column_name = 'is_public';

-- Debería retornar:
-- column_name | data_type | column_default
-- is_public   | boolean   | false

-- Verificar tabla user_sessions
SELECT EXISTS (
    SELECT FROM information_schema.tables
    WHERE table_schema = 'public'
    AND table_name = 'user_sessions'
);

-- Debería retornar: true
```

## Problemas Comunes

### Error: "permission denied"
**Solución**: Asegúrate de tener permisos de administrador en PostgreSQL.

```sql
-- Otorgar permisos si es necesario
GRANT ALL PRIVILEGES ON DATABASE nombre_base_datos TO tu_usuario;
```

### Error: "relation 'games' does not exist"
**Solución**: La base de datos está vacía. Simplemente inicia el servidor Ktor y las tablas se crearán automáticamente.

### Error: "column is_public already exists"
**Solución**: La migración ya fue ejecutada. Puedes ignorar este error o usar `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`.

## Después de la Migración

1. **Reinicia el servidor Ktor**
   ```bash
   ./gradlew run
   ```

2. **Verifica los logs del servidor**
   Deberías ver:
   ```
   Base de datos configurada, cartas y tablero inicializados correctamente
   ```

3. **Prueba la app**
   - Login debería funcionar
   - Crear partida privada debería funcionar
   - WebSocket debería conectar correctamente

## Si Sigues Teniendo Problemas

### Limpiar partidas huérfanas (opcional)

Si tienes partidas sin el campo `is_public`:

```sql
-- Ver partidas sin is_public
SELECT id, code, is_public FROM games WHERE is_public IS NULL;

-- Actualizar todas a privadas por defecto
UPDATE games SET is_public = false WHERE is_public IS NULL;
```

### Limpiar sesiones antiguas (opcional)

```sql
-- Eliminar sesiones antiguas (más de 7 días)
DELETE FROM user_sessions
WHERE created_at < NOW() - INTERVAL '7 days';
```

### Verificar estructura completa

```sql
-- Ver todas las tablas
\dt

-- Debería mostrar:
-- public | board_squares
-- public | cards
-- public | chat_messages
-- public | game_decks
-- public | game_players
-- public | games
-- public | player_effects
-- public | player_hands
-- public | user_sessions ← NUEVA
-- public | users
```

## Rollback (si necesitas revertir cambios)

Si algo sale mal y necesitas volver atrás:

```sql
-- SOLO si necesitas revertir
DROP TABLE IF EXISTS user_sessions CASCADE;
ALTER TABLE games DROP COLUMN IF EXISTS is_public;
```

---

## Contacto

Si tienes problemas con la migración, revisa:
1. Los logs del servidor Ktor
2. Los logs de PostgreSQL
3. El archivo `CAMBIOS_APLICADOS.md` para más detalles
