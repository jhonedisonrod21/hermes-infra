package co.com.hermes.calendar.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documento OpenAPI propio del gateway: un mapa de RUTAS y SEGURIDAD, no el catalogo de transacciones.
 *
 * <p>Los contratos detallados (payloads, parametros, esquemas) los publica cada microservicio en su
 * propio {@code /v3/api-docs}, y el Swagger UI del gateway los reune como grupos via
 * {@code springdoc.swagger-ui.urls} (ver hermes-api-gateway.yml). Aqui solo se describe lo transversal
 * que ningun servicio conoce: que rutas existen, que rol/permiso exige cada una, y como el gateway
 * deriva del JWT las cabeceras de confianza X-Hermes-* que propaga a los servicios.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hermes API Gateway - rutas y seguridad")
                        .version("0.0.1")
                        .description("""
                                Puerta de entrada de Hermes. Enruta a microservicios por Eureka, valida los JWT \
                                emitidos por Auth Server y recorta el prefijo de ruta (StripPrefix=1) antes de reenviar.

                                El cliente solo envia 'Authorization: Bearer <JWT>'. El gateway DERIVA del token y \
                                propaga a los servicios cabeceras de confianza (descartando cualquier valor que el \
                                cliente intente enviar): X-Hermes-User-Id, X-Hermes-Username, X-Hermes-Account-Scope, \
                                X-Hermes-Tenant-Id, X-Hermes-Tenant-Slug, X-Hermes-Roles, X-Hermes-Permissions.

                                Los contratos por endpoint (cuerpos, parametros y esquemas) estan en los grupos por \
                                servicio del selector de Swagger UI (auth-server, identity-service, tenant-service, \
                                catalog-service, scheduling-service)."""))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .paths(new Paths()
                        .addPathItem("/auth/**", route("Auth Server route", "OAuth2/OIDC y login de sesion. Publico. "
                                + "Detalle en el grupo 'auth-server'.", false))
                        .addPathItem("/bff/**", route("Web BFF route", "Sesion web, OAuth2 client y API proxy a hermes-web-bff. Publico.", false))
                        .addPathItem("/identity/users/register", route("Registro publico de usuarios", "Crea una cuenta invitada "
                                + "(GUEST_USER). Publico. Detalle en el grupo 'identity-service'.", false))
                        .addPathItem("/identity/admin/**", route("Identity admin route", "Administracion de usuarios. Requiere rol SYSTEM_ADMIN.", true))
                        .addPathItem("/identity/**", route("Identity Service route", "Identidad. Requiere SYSTEM_ADMIN o TENANT_ADMIN. "
                                + "Detalle en el grupo 'identity-service'.", true))
                        .addPathItem("/tenant/admin/**", route("Tenant admin route", "Administracion de establecimientos y membresias. "
                                + "Requiere rol SYSTEM_ADMIN.", true))
                        .addPathItem("/tenant/**", route("Tenant Service route", "Establecimientos. Requiere SYSTEM_ADMIN o TENANT_ADMIN. "
                                + "Detalle en el grupo 'tenant-service'.", true))
                        .addPathItem("/catalog/search", route("Catalog search route", "Busqueda publica de servicios activos. "
                                + "Cualquier usuario autenticado (incluido GUEST_USER).", true))
                        .addPathItem("/catalog/**", route("Catalog Service route", "Catalogo agendable. Requiere permiso calendar:read o "
                                + "calendar:write (TENANT_ADMIN o TENANT_PARTNER). Detalle en el grupo 'catalog-service'.", true))
                        .addPathItem("/scheduling/**", route("Scheduling Service route", "Horario y agenda. Requiere calendar:read o "
                                + "calendar:write. Detalle en el grupo 'scheduling-service'.", true))
                        .addPathItem("/payment/**", route("Payment Service route", "Pagos/facturacion. Requiere rol SYSTEM_ADMIN o TENANT_ADMIN.", true))
                        .addPathItem("/notification/**", route("Notification Service route", "Notificaciones. Requiere token autenticado.", true))
                        .addPathItem("/integration/**", route("Integration Hub route", "Integraciones externas. Requiere rol SYSTEM_ADMIN o TENANT_ADMIN.", true))
                        .addPathItem("/*/internal/**", denied("Rutas internas bloqueadas", "El gateway bloquea el acceso externo a "
                                + "endpoints internos (p. ej. /identity/internal/**, /tenant/internal/**), reservados a la "
                                + "comunicacion servicio-a-servicio con X-Hermes-Internal-Key.")));
    }

    private PathItem route(String summary, String description, boolean secured) {
        Operation operation = new Operation()
                .tags(List.of("Gateway routes"))
                .summary(summary)
                .description(description)
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Respuesta del microservicio destino."))
                        .addApiResponse("401", new ApiResponse().description("Token ausente o invalido."))
                        .addApiResponse("403", new ApiResponse().description("Permisos insuficientes."))
                        .addApiResponse("503", new ApiResponse().description("Servicio destino no disponible en Eureka.")));
        if (secured) {
            operation.addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
        }
        return new PathItem().get(operation).post(operation).put(operation).delete(operation);
    }

    private PathItem denied(String summary, String description) {
        Operation operation = new Operation()
                .tags(List.of("Gateway security"))
                .summary(summary)
                .description(description)
                .responses(new ApiResponses()
                        .addApiResponse("403", new ApiResponse().description("Ruta interna bloqueada por gateway.")));
        return new PathItem().get(operation).post(operation).put(operation).delete(operation);
    }
}
