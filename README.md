# hermes-infra — Infraestructura / Borde (Edge)

Capa de **plataforma** de Hermes: **sin estado, sin base de datos**. Es el borde
del sistema (configuración, descubrimiento, enrutamiento y BFF del frontend).

## Propósito
Proveer la infraestructura transversal: configuración centralizada,
descubrimiento de servicios, gateway de borde y backend del SPA.

## Servicios
| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| hermes-config-server | 8888 | Spring Cloud Config (backend `native`, sirve `config-repo`) |
| hermes-service-registry | 8761 | Eureka Server (descubrimiento) |
| hermes-api-gateway | 8080 | Rutas `lb://`, resource server JWT, inyecta cabeceras `X-Hermes-*` y **deniega** `/*/internal/**` |
| hermes-web-bff | 8090 | BFF del SPA: cliente OAuth2 + sesión cookie + proxy `/api` con token relay |

## Arquitectura
- El **config-server** sirve la configuración de todos los servicios. El perfil
  `native` se incluye siempre (`spring.profiles.include`) para no depender del
  perfil de app (`local`/`dev`).
- El **gateway** valida el JWT, traduce la identidad del token a cabeceras
  `X-Hermes-*` (borrando primero las entrantes → anti-spoofing) y bloquea las
  rutas internas.
- El **web-bff** es el único componente que toca el navegador: mantiene la sesión
  (cookie `HERMES_BFF_SESSION`), ejecuta el flujo OAuth2 (authorization code +
  PKCE) y reenvía a `/api` con el access token. **El navegador nunca ve tokens.**

## Consideraciones técnicas
- Sin base de datos.
- El BFF vive aquí (no en `hermes-security`) por ser **borde/consumidor** del
  sistema de auth y estar atado al SPA, no a la autoridad de identidad
  (ver `ARCHITECTURE.md`).
- Perfiles `local` (por defecto) / `dev`.

## Construir / correr
```bash
./gradlew build
# Stack completo (desde la carpeta padre): ../hermes-stack.sh up
```

## Stack
Java 25 · Gradle 9.5 · Spring Boot 4.0.6 / Spring Cloud 2025.1.1.
