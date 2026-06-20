# Perfiles de entorno (Hermes Config Server)

El Config Server fusiona la configuración por capas. Para `{servicio}` con perfil
`{perfil}`, el orden de precedencia es (gana el de arriba):

```
{servicio}-{perfil}.yml     ← máxima precedencia
{servicio}.yml
application-{perfil}.yml
application.yml             ← mínima precedencia
```

El perfil activo lo decide el **cliente** (cada servicio) con la variable de
entorno `SPRING_PROFILES_ACTIVE`, porque viaja en la petición al Config Server.

## Perfiles disponibles

| Perfil  | Cómo se activa | Infraestructura |
|---------|----------------|-----------------|
| `local` | automático con `./gradlew startLocalServices` (lo fija `build.gradle`) | Contenedores locales: MySQL `localhost:3306/HERMES`, SonarQube `localhost:9001` |
| `dev`   | `SPRING_PROFILES_ACTIVE=dev` en el contenedor/orquestador | Nube; todo por variables de entorno, secretos obligatorios |

> Sin perfil (`SPRING_PROFILES_ACTIVE` vacío) se usan solo los archivos base,
> que ya apuntan a los contenedores locales. El perfil `local` añade encima
> comodidades de desarrollo (logs DEBUG, `show-details: always`, `format_sql`).

## Capas

- **`application.yml` / `{servicio}.yml`** — base: estructura + valores cómodos de local.
- **`application-local.yml`** — comodidades de desarrollo (no toca conexiones).
- **`application-dev.yml`** — transversal de nube: Eureka, logs, actuator, issuer.
- **`{servicio}-dev.yml`** — endurece los secretos de ESE servicio (sin default → fail-fast).

## Fail-fast de secretos en `dev`

En los overlays `-dev.yml` los secretos se declaran **sin valor por defecto**
(`${VAR}` en vez de `${VAR:default}`). Si la variable de entorno no está
definida, el servicio **no arranca**, en lugar de caer al valor de desarrollo.

Variables que `dev` exige (mínimo para el núcleo):

```
EUREKA_DEFAULT_ZONE
HERMES_AUTH_ISSUER_URI  HERMES_AUTH_PUBLIC_BASE_URL  HERMES_AUTH_GATEWAY_BASE_URL  HERMES_AUTH_JWK_SET_URI
HERMES_DATASOURCE_URL   HERMES_DATASOURCE_PASSWORD
HERMES_INTERNAL_API_KEY
HERMES_WEB_CLIENT_SECRET   (codificado, p.ej. {bcrypt}...)   HERMES_WEB_CLIENT_REDIRECT_URIS  HERMES_WEB_BFF_REDIRECT_URI
HERMES_AUTH_JWK_PUBLIC_KEY_PEM   HERMES_AUTH_JWK_PRIVATE_KEY_PEM
HERMES_WEB_FRONTEND_URL   HERMES_WEB_ALLOWED_ORIGINS   HERMES_GATEWAY_BASE_URL
```

## SonarQube

SonarQube es de **build-time**, no runtime: se usa con `./gradlew sonar`. La URL
se resuelve en `build.gradle` con `SONAR_HOST_URL` (por defecto el contenedor
local `http://localhost:9001`). Para la nube: exporta `SONAR_HOST_URL` y
`SONAR_TOKEN`.

## Servicios de dominio

Los overlays `-dev.yml` cubren el núcleo (auth, identity, tenant, web-bff,
gateway). Los servicios de dominio (catalog, scheduling, payment, notification,
integration-hub) heredan `application-dev.yml`; al implementarlos, añade su
`{servicio}-dev.yml` con el datasource en modo fail-fast siguiendo el mismo patrón.
