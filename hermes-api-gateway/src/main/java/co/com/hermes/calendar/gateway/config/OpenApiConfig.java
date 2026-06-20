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

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hermes API Gateway")
                        .version("0.0.1")
                        .description("Puerta de entrada de Hermes. Enruta trafico a microservicios por Eureka, valida JWT "
                        + "emitidos por Auth Server y propaga headers confiables X-Hermes-* derivados del token."))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .paths(new Paths()
                        .addPathItem("/auth/**", route("Auth Server route", "Enruta OAuth2/OIDC y login a hermes-auth-server.", false))
                        .addPathItem("/bff/**", route("Web BFF route", "Enruta sesion web, OAuth2 client y API proxy a hermes-web-bff.", false))
                        .addPathItem("/identity/users/register", route("Registro publico de usuarios", "Permite crear cuenta con correo y password. "
                                + "Identity aprovisiona el tenant inicial mediante Tenant Service.", false))
                        .addPathItem("/identity/**", route("Identity Service route", "Enruta operaciones de identidad. Requiere rol OWNER o ADMIN.", true))
                        .addPathItem("/tenant/**", route("Tenant Service route", "Enruta operaciones de tenants. Requiere permiso tenant:manage.", true))
                        .addPathItem("/catalog/**", route("Catalog Service route", "Enruta categorias, productos y servicios agendables por tenant. "
                                + "Requiere rol OWNER o ADMIN.", true))
                        .addPathItem("/scheduling/**", route("Scheduling Service route", "Enruta calendario y agenda. Requiere calendar:read o calendar:write.", true))
                        .addPathItem("/payment/**", route("Payment Service route", "Enruta pagos/facturacion. Requiere billing:read o billing:write.", true))
                        .addPathItem("/notification/**", route("Notification Service route", "Enruta notificaciones. Requiere token autenticado.", true))
                        .addPathItem("/integration/**", route("Integration Hub route", "Enruta integraciones externas. Requiere rol OWNER o ADMIN.", true))
                        .addPathItem("/*/internal/**", denied("Rutas internas bloqueadas", "El gateway bloquea cualquier intento de acceder a endpoints internos.")));
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
