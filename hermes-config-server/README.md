# Hermes Config Server

Spring Cloud Config Server centraliza la configuracion de los servicios Hermes.

## Arranque local

0. Compila con el Gradle Wrapper del proyecto:

```bash
./gradlew compileJava
```

1. Inicia `hermes-config-server` en el puerto `${CONFIG_SERVER_PORT:8888}`.
2. Inicia `hermes-service-registry` en el puerto `${HERMES_SERVICE_REGISTRY_PORT:8761}`.
3. Inicia `hermes-identity-service`.
4. Inicia `hermes-tenant-service`.
5. Inicia `hermes-auth-server` en el puerto `${HERMES_AUTH_SERVER_PORT:9000}`.
6. Inicia `hermes-api-gateway`.
7. Inicia `hermes-web-bff`.
8. Inicia microservicios de dominio.

Tambien puedes usar la tarea Gradle del proyecto raiz:

```bash
./gradlew startLocalServices
```

El perfil por defecto arranca los servicios core. La tarea detiene primero procesos Hermes que sigan usando los puertos locales, recompila los `bootJar`, espera healthchecks, valida la metadata OIDC del auth-server via gateway antes de iniciar `hermes-web-bff` y valida `/bff/session/me` via gateway antes de finalizar.

Para incluir todos los servicios de dominio:

```bash
./gradlew startLocalServices -PhermesRunProfile=all
```

Para detener los procesos:

```bash
./gradlew stopLocalServices
```

Los clientes cargan su configuracion desde:

```text
${CONFIG_SERVER_URL:http://localhost:8888}/{spring.application.name}/default
```

El repositorio de configuracion local vive en:

```text
src/main/resources/config-repo
```

## Variables principales

Los valores tienen defaults para desarrollo local, pero pueden cambiarse por variables de entorno durante el arranque.

| Variable | Default |
| --- | --- |
| `CONFIG_SERVER_URL` | `http://localhost:8888` |
| `CONFIG_SERVER_PORT` | `8888` |
| `CONFIG_REPO_SEARCH_LOCATIONS` | `classpath:/config-repo` |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/` |
| `HERMES_AUTH_ISSUER_URI` | `http://127.0.0.1:5173/auth` |
| `HERMES_AUTH_JWK_SET_URI` | `http://127.0.0.1:9000/oauth2/jwks` |
| `HERMES_IDENTITY_BASE_URL` | `http://hermes-identity-service` |
| `HERMES_TENANT_BASE_URL` | `http://hermes-tenant-service` |
| `HERMES_INTERNAL_API_KEY` | `local-dev-internal-key` |
| `HERMES_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/hermes_calendar?currentSchema=hermes` |
| `HERMES_DATASOURCE_USERNAME` | `hermes_app` |
| `HERMES_DATASOURCE_PASSWORD` | `hermes_app_local` |
| `HERMES_DB_SCHEMA` | `hermes` |
| `HERMES_DATASOURCE_MAX_POOL_SIZE` | `10` |
| `HERMES_DATASOURCE_MIN_IDLE` | `2` |
| `HERMES_FLYWAY_ENABLED` | `true` |
| `HERMES_FLYWAY_LOCATIONS` | `classpath:db/migration` |
| `HERMES_FLYWAY_VALIDATE_ON_MIGRATE` | `true` |
| `HERMES_FLYWAY_CLEAN_DISABLED` | `true` |
| `HERMES_FLYWAY_BASELINE_ON_MIGRATE` | `false` |
| `HERMES_AUTH_JWK_KEY_ID` | `hermes-auth-local-key` |
| `HERMES_AUTH_JWK_PUBLIC_KEY_PEM` | empty, generated in memory |
| `HERMES_AUTH_JWK_PRIVATE_KEY_PEM` | empty, generated in memory |

Cada servicio con base de datos tambien permite sobreescritura especifica, por ejemplo:

```text
HERMES_IDENTITY_DATASOURCE_URL
HERMES_IDENTITY_DATASOURCE_USERNAME
HERMES_IDENTITY_DATASOURCE_PASSWORD
HERMES_IDENTITY_DB_SCHEMA
HERMES_IDENTITY_FLYWAY_LOCATIONS
```

## Convencion Flyway

Los servicios que dependen directamente de PostgreSQL son:

```text
hermes-auth-server
hermes-identity-service
hermes-tenant-service
hermes-scheduling-service
hermes-payment-service
hermes-notification-service
hermes-integration-hub-service
```

Cada uno debe versionar sus migraciones en:

```text
src/main/resources/db/migration
```

Ejemplo de nombre:

```text
V1__create_initial_tables.sql
```

Hibernate queda en `ddl-auto: validate`, de modo que Flyway crea o modifica tablas y JPA solo valida que el modelo coincida con la base.

Cada servicio usa su propia tabla de historial Flyway cuando comparte el schema local `hermes`, por ejemplo:

```text
flyway_schema_history_auth
flyway_schema_history_identity
flyway_schema_history_tenant
```

## Arranque Auth Inicial

La primera integracion de autenticacion usa esta separacion:

```text
hermes-identity-service: usuarios, roles y verificacion interna de credenciales
hermes-tenant-service: tenants, membresias, roles y permisos por tenant
hermes-auth-server: OAuth2/OIDC y emision de JWT
hermes-api-gateway: validacion de JWT, roles, scopes y rutas
hermes-web-bff: sesion web, OAuth2 Client y proxy API server-side
```

Usuario local sembrado por Flyway en `hermes-identity-service`:

```text
username: admin@hermes.local
password: admin123
role: ADMIN
tenant: hermes-local
tenant role: OWNER
```

Cliente OAuth2 local:

```text
client_id: hermes-web-client
client_secret: hermes-web-secret
redirect_uri: http://127.0.0.1:5173/bff/login/oauth2/code/hermes-web-client
```

Los clientes OAuth2, autorizaciones y consentimientos del Auth Server se guardan en PostgreSQL mediante las tablas:

```text
oauth2_registered_client
oauth2_authorization
oauth2_authorization_consent
```

El cliente local se siembra al arranque si no existe. Las migraciones `V3` y `V4` corrigen clientes existentes para mover el callback del BFF al gateway.

## Healthchecks

Todos los servicios que consumen Config Server exponen:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
```

## JWT

Auth Server puede cargar una llave RSA persistente con:

```text
HERMES_AUTH_JWK_KEY_ID
HERMES_AUTH_JWK_PUBLIC_KEY_PEM
HERMES_AUTH_JWK_PRIVATE_KEY_PEM
```

Si no se configuran PEMs, genera una llave RSA en memoria para desarrollo local. En ese caso, los tokens emitidos antes de reiniciar Auth dejan de validar.

Identity expone el contrato interno:

```text
POST /internal/auth/credentials/verify
Header: X-Hermes-Internal-Key: ${HERMES_INTERNAL_API_KEY:local-dev-internal-key}
```

Tenant expone el contrato interno:

```text
GET /internal/users/{userId}/tenant-context/default
Header: X-Hermes-Internal-Key: ${HERMES_INTERNAL_API_KEY:local-dev-internal-key}
```

Gateway enruta:

```text
/auth/** -> hermes-auth-server
/bff/** -> hermes-web-bff
/identity/** -> hermes-identity-service
/tenant/** -> hermes-tenant-service
/scheduling/** -> hermes-scheduling-service
/payment/** -> hermes-payment-service
/notification/** -> hermes-notification-service
/integration/** -> hermes-integration-hub-service
```

El Gateway no usa discovery locator automatico. Solo expone rutas declaradas de forma explicita y bloquea:

```text
/*/internal/**
/*/actuator/**
```

Tambien limpia headers `X-Hermes-*` enviados por clientes externos y los vuelve a crear desde el JWT validado:

```text
X-Hermes-User-Id
X-Hermes-Username
X-Hermes-Tenant-Id
X-Hermes-Tenant-Slug
X-Hermes-Roles
X-Hermes-Permissions
```

## Seguridad Web BFF

El flujo web usa un solo host publico para el navegador:

```text
http://127.0.0.1:8080
```

React usa rutas relativas y el gateway decide el destino:

```text
/auth/session/login -> hermes-auth-server
/bff/oauth2/authorization/hermes-web-client -> hermes-web-bff
/bff/session/me -> hermes-web-bff
/bff/api/** -> hermes-web-bff -> hermes-api-gateway -> microservicio
```

`hermes-web-bff` maneja Authorization Code + PKCE como cliente OAuth2 confidencial, almacena tokens en la sesion del servidor y devuelve al navegador una cookie HttpOnly `HERMES_BFF_SESSION`. El front no debe persistir access tokens ni refresh tokens.

El issuer publicado en tokens es `HERMES_AUTH_ISSUER_URI`. Para desarrollo local apunta al origen visible del navegador (`http://127.0.0.1:5173/auth`) y los resource servers usan `HERMES_AUTH_JWK_SET_URI` para resolver JWKs internamente desde el auth-server sin depender del proxy de Vite.
