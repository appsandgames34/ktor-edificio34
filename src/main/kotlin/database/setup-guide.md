# Guía de Configuración - Edificio 34 Server

## Requisitos Previos

- **Java JDK 11 o superior**
- **PostgreSQL 12 o superior**
- **Gradle** (incluido en el proyecto vía wrapper)

---

## Paso 1: Instalar PostgreSQL

### En Ubuntu/Debian:
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
```

### En macOS (con Homebrew):
```bash
brew install postgresql
brew services start postgresql
```

### En Windows:
Descargar instalador desde [postgresql.org](https://www.postgresql.org/download/windows/)

---

## Paso 2: Configurar Base de Datos

### 1. Acceder a PostgreSQL
```bash
sudo -u postgres psql
```

### 2. Crear base de datos y usuario
```sql
CREATE DATABASE edificio34;
CREATE USER edificio_admin WITH PASSWORD 'tu_password_segura';
GRANT ALL PRIVILEGES ON DATABASE edificio34 TO edificio_admin;
\q
```

### 3. (Opcional) Verificar conexión
```bash
psql -U edificio_admin -d edificio34 -h localhost
```

---

## Paso 3: Configurar el Proyecto

### 1. Clonar/Abrir el proyecto
```bash
cd ktor-edificio34
```

### 2. Configurar credenciales de base de datos

Editar `src/main/resources/application.yaml`:

```yaml
ktor:
  deployment:
    port: 8080
  application:
    modules:
      - com.appsandgames34.ApplicationKt.module

postgres:
  url: "jdbc:postgresql://localhost:5432/edificio34"
  user: "edificio_admin"
  password: "tu_password_segura"
```

**⚠️ IMPORTANTE:** Nunca subas este archivo con credenciales reales a Git.

### 3. (Opcional) Variables de entorno

Puedes usar variables de entorno en lugar de hardcodear las credenciales:

```bash
export DB_URL="jdbc:postgresql://localhost:5432/edificio34"
export DB_USER="edificio_admin"
export DB_PASSWORD="tu_password_segura"
```

Y modificar `application.yaml`:
```yaml
postgres:
  url: ${?DB_URL}
  user: ${?DB_USER}
  password: ${?DB_PASSWORD}
```

---

## Paso 4: Agregar Dependencia de BCrypt

En `build.gradle.kts`, agregar:

```kotlin
dependencies {
    // ... otras dependencias
    implementation("org.mindrot:jbcrypt:0.4")
}
```

---

## Paso 5: Construir y Ejecutar

### Desarrollo (con hot-reload)
```bash
./gradlew run
```

### Construir JAR ejecutable
```bash
./gradlew buildFatJar
```

### Ejecutar JAR
```bash
java -jar build/libs/ktor-edificio34-all.jar
```

---

## Paso 6: Verificar Instalación

### 1. El servidor debería mostrar:
```
Base de datos configurada y cartas inicializadas correctamente
20 cartas inicializadas correctamente
Responding at http://0.0.0.0:8080
```

### 2. Probar endpoint de salud
```bash
curl http://localhost:8080/cards/info
```

Deberías ver la lista de 20 cartas.

---

## Paso 7: Probar la API

### Opción 1: Con curl

```bash
# Registrar usuario
curl -X POST http://localhost:8080/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test1","email":"test1@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test1","password":"password123"}'

# Guardar el token de la respuesta y usarlo:
export TOKEN="tu_token_jwt_aqui"

# Crear partida
curl -X POST http://localhost:8080/games/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"maxPlayers":4,"isPublic":false}'
```

### Opción 2: Con Postman
1. Importar el archivo `test-requests.http` como colección
2. Configurar variables de entorno en Postman
3. Ejecutar las peticiones en orden

### Opción 3: Con REST Client (VS Code)
1. Instalar extensión "REST Client"
2. Abrir `test-requests.http`
3. Hacer clic en "Send Request" sobre cada petición

---

## Estructura del Proyecto

```
ktor-edificio34/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   ├── com/appsandgames34/
│   │   │   │   ├── modelos/          # Modelos de datos (Exposed)
│   │   │   │   │   ├── Users.kt
│   │   │   │   │   ├── Games.kt
│   │   │   │   │   ├── GamePlayers.kt
│   │   │   │   │   ├── Cards.kt
│   │   │   │   │   ├── GameDecks.kt
│   │   │   │   │   ├── PlayerHands.kt
│   │   │   │   │   └── ChatMessages.kt
│   │   │   │   ├── routes/           # Endpoints de la API
│   │   │   │   │   ├── UserRoutes.kt
│   │   │   │   │   ├── GameRoutes.kt
│   │   │   │   │   ├── CardRoutes.kt
│   │   │   │   │   ├── ChatRoutes.kt
│   │   │   │   │   └── WebSocketRoutes.kt
│   │   │   │   ├── util/             # Utilidades
│   │   │   │   │   ├── JwtConfig.kt
│   │   │   │   │   ├── CardInitializer.kt
│   │   │   │   │   └── DTOs.kt
│   │   │   │   ├── Application.kt    # Punto de entrada
│   │   │   │   ├── Databases.kt      # Configuración BD
│   │   │   │   ├── Routing.kt        # Configuración rutas
│   │   │   │   ├── Security.kt       # Configuración JWT
│   │   │   │   ├── HTTP.kt           # Configuración CORS
│   │   │   │   ├── Serialization.kt  # Configuración JSON
│   │   │   │   └── Monitoring.kt     # Logs
│   │   └── resources/
│   │       ├── application.yaml      # Configuración app
│   │       └── logback.xml           # Configuración logs
│   └── test/
│       └── kotlin/
│           └── ApplicationTest.kt
├── build.gradle.kts                  # Dependencias Gradle
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## Configuración Adicional

### 1. Configurar JWT Secret (Producción)

Editar `src/main/kotlin/util/JwtConfig.kt`:

```kotlin
object JwtConfig {
    private val secret = System.getenv("JWT_SECRET") ?: "super_secret_key"
    // ... resto del código
}
```

Y establecer la variable de entorno:
```bash
export JWT_SECRET="tu_clave_secreta_muy_larga_y_segura_aqui"
```

### 2. Configurar CORS (Producción)

Editar `src/main/kotlin/HTTP.kt`:

```kotlin
fun Application.configureHTTP() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        
        // En producción, especificar dominios exactos:
        allowHost("tudominio.com", schemes = listOf("https"))
        allowHost("app.tudominio.com", schemes = listOf("https"))
        
        // NO usar anyHost() en producción
    }
}
```

### 3. SSL/HTTPS (Producción)

Para producción, usar un reverse proxy como Nginx:

```nginx
server {
    listen 443 ssl;
    server_name api.tudominio.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
    }
}
```

---

## Solución de Problemas

### Error: "Connection refused" al conectar a PostgreSQL

**Solución:**
```bash
# Verificar que PostgreSQL esté corriendo
sudo systemctl status postgresql

# Iniciar si está detenido
sudo systemctl start postgresql

# Verificar puerto
sudo netstat -plunt | grep 5432
```

### Error: "Database does not exist"

**Solución:**
```bash
sudo -u postgres psql -c "CREATE DATABASE edificio34;"
```

### Error: "Authentication failed for user"

**Solución:**
1. Verificar credenciales en `application.yaml`
2. Recrear usuario:
```sql
DROP USER IF EXISTS edificio_admin;
CREATE USER edificio_admin WITH PASSWORD 'nueva_password';
GRANT ALL PRIVILEGES ON DATABASE edificio34 TO edificio_admin;
```

### Error: "Port 8080 already in use"

**Solución:**
```bash
# Encontrar proceso usando el puerto
sudo lsof -i :8080

# Matar proceso
kill -9 <PID>

# O cambiar puerto en application.yaml
```

### Error: BCrypt no encontrado

**Solución:**
```bash
# Agregar dependencia en build.gradle.kts
implementation("org.mindrot:jbcrypt:0.4")

# Recompilar
./gradlew clean build
```

### Las tablas no se crean automáticamente

**Solución:**
1. Verificar que Exposed esté correctamente configurado
2. Usar el script SQL manual: `database-schema.sql`
```bash
psql -U edificio_admin -d edificio34 -f database-schema.sql
```

---

## Despliegue en Producción

### Opción 1: Docker

Crear `Dockerfile`:

```dockerfile
FROM gradle:8.5-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon

FROM openjdk:17-jre-slim
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/ktor-app.jar
ENTRYPOINT ["java", "-jar", "/app/ktor-app.jar"]
```

Crear `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: edificio34
      POSTGRES_USER: edificio_admin
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/edificio34
      DB_USER: edificio_admin
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      - postgres

volumes:
  postgres_data:
```

Ejecutar:
```bash
docker-compose up -d
```

### Opción 2: VPS tradicional

```bash
# 1. Instalar Java
sudo apt update
sudo apt install openjdk-17-jre

# 2. Copiar JAR al servidor
scp build/libs/ktor-edificio34-all.jar user@servidor:/opt/edificio34/

# 3. Crear servicio systemd
sudo nano /etc/systemd/system/edificio34.service
```

Contenido del servicio:
```ini
[Unit]
Description=Edificio 34 Game Server
After=network.target postgresql.service

[Service]
Type=simple
User=edificio34
WorkingDirectory=/opt/edificio34
ExecStart=/usr/bin/java -jar /opt/edificio34/ktor-edificio34-all.jar
Restart=on-failure
Environment="DB_URL=jdbc:postgresql://localhost:5432/edificio34"
Environment="DB_USER=edificio_admin"
Environment="DB_PASSWORD=tu_password"
Environment="JWT_SECRET=tu_secret"

[Install]
WantedBy=multi-user.target
```

Activar:
```bash
sudo systemctl enable edificio34
sudo systemctl start edificio34
sudo systemctl status edificio34
```

---

## Monitoreo y Logs

### Ver logs en tiempo real
```bash
# Con systemd
sudo journalctl -u edificio34 -f

# Con Docker
docker-compose logs -f api

# Archivo de logs (si se configuró)
tail -f /var/log/edificio34/app.log
```

### Métricas básicas

Agregar endpoint de salud:

```kotlin
// En Routing.kt
routing {
    get("/health") {
        call.respond(mapOf(
            "status" to "UP",
            "timestamp" to System.currentTimeMillis()
        ))
    }
}
```

---

## Backup de Base de Datos

### Backup manual
```bash
pg_dump -U edificio_admin edificio34 > backup_$(date +%Y%m%d).sql
```

### Restaurar backup
```bash
psql -U edificio_admin edificio34 < backup_20251014.sql
```

### Backup automatizado (cron)
```bash
# Editar crontab
crontab -e

# Agregar línea para backup diario a las 3 AM
0 3 * * * pg_dump -U edificio_admin edificio34 > /backups/edificio34_$(date +\%Y\%m\%d).sql
```

---

## Integración con KMP Client

### Ejemplo de código Kotlin Multiplatform:

```kotlin
// commonMain
class GameApiClient(private val baseUrl: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets)
    }
    
    suspend fun login(username: String, password: String): LoginResponse {
        return client.post("$baseUrl/users/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }.body()
    }
    
    suspend fun connectToGame(gameId: String, token: String) {
        client.webSocket("$baseUrl/ws/game/$gameId") {
            // Manejar mensajes del WebSocket
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    // Procesar mensaje
                }
            }
        }
    }
}
```

---

## Próximos Pasos

1. ✅ Configurar base de datos
2. ✅ Ejecutar servidor
3. ✅ Probar endpoints básicos
4. 🔲 Desarrollar cliente KMP
5. 🔲 Implementar UI del juego
6. 🔲 Pruebas de carga
7. 🔲 Deploy a producción

---

## Recursos Adicionales

- [Documentación Ktor](https://ktor.io/docs/)
- [Documentación Exposed](https://github.com/JetBrains/Exposed)
- [JWT Best Practices](https://jwt.io/introduction)
- [PostgreSQL Docs](https://www.postgresql.org/docs/)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)

---

## Soporte

Para problemas o preguntas:
- Revisar logs del servidor
- Verificar configuración de base de datos
- Consultar documentación de la API
- Abrir issue en el repositorio