package co.com.hermes.calendar.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Roles del modelo de 4 actores (hasAnyRole antepone el prefijo ROLE_).
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";   // plataforma, sin tenant
    private static final String TENANT_ADMIN = "TENANT_ADMIN";   // administrador de la organizacion
    // GUEST_USER (invitado sin tenant) y TENANT_PARTNER no se nombran aqui: el primero solo
    // alcanza rutas "authenticated"; el segundo entra por los permisos de calendario.

    // Permisos de operacion de agenda (los portan TENANT_ADMIN y TENANT_PARTNER).
    private static final String CALENDAR_READ = "calendar:read";
    private static final String CALENDAR_WRITE = "calendar:write";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/**",
                                // Specs OpenAPI de cada microservicio, descargados por Swagger UI a traves del
                                // gateway para la vista agregada. Solo exponen el contrato (no datos); /auth/**
                                // ya es publico. Deben ir antes de las reglas /identity/**, /tenant/**, etc.
                                "/identity/v3/api-docs/**",
                                "/tenant/v3/api-docs/**",
                                "/catalog/v3/api-docs/**",
                                "/scheduling/v3/api-docs/**"
                        ).permitAll()
                        .pathMatchers("/*/internal/**", "/*/actuator/**").denyAll()
                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/bff/**").permitAll()
                        .pathMatchers("/identity/users/register").permitAll()
                        // Gestion de establecimientos y de usuarios: exclusiva del administrador del sistema
                        // (deben ir antes de las reglas generales /tenant/** y /identity/**).
                        .pathMatchers("/tenant/admin/**").hasRole(SYSTEM_ADMIN)
                        .pathMatchers("/identity/admin/**").hasRole(SYSTEM_ADMIN)
                        // Administracion: administrador del sistema (plataforma) y de la organizacion.
                        .pathMatchers("/identity/**").hasAnyRole(SYSTEM_ADMIN, TENANT_ADMIN)
                        .pathMatchers("/tenant/**").hasAnyRole(SYSTEM_ADMIN, TENANT_ADMIN)
                        .pathMatchers("/integration/**").hasAnyRole(SYSTEM_ADMIN, TENANT_ADMIN)
                        .pathMatchers("/payment/**").hasAnyRole(SYSTEM_ADMIN, TENANT_ADMIN)
                        // Busqueda publica del catalogo: cualquier usuario autenticado (incluido GUEST_USER,
                        // que no tiene calendar:read). Debe ir antes de la regla general /catalog/**.
                        .pathMatchers("/catalog/search").authenticated()
                        // Operacion del tenant (catalogo agendable y agenda): miembros con permisos de
                        // calendario -> TENANT_ADMIN y TENANT_PARTNER. El servicio destino restringe escritura.
                        .pathMatchers("/catalog/**").hasAnyAuthority(CALENDAR_READ, CALENDAR_WRITE)
                        .pathMatchers("/scheduling/**").hasAnyAuthority(CALENDAR_READ, CALENDAR_WRITE)
                        // Basta token autenticado (incluye al GUEST_USER invitado sin tenant).
                        .pathMatchers("/notification/**").authenticated()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> Mono.just(new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject()));
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<String> scopes = claimAsList(jwt, "scope");
        List<String> roles = claimAsList(jwt, "roles");
        List<String> permissions = claimAsList(jwt, "permissions");

        Stream<GrantedAuthority> scopeAuthorities = nullSafe(scopes)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope));
        Stream<GrantedAuthority> roleAuthorities = nullSafe(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role));
        Stream<GrantedAuthority> permissionAuthorities = nullSafe(permissions)
                .map(permission -> new SimpleGrantedAuthority(permission));

        return Stream.of(scopeAuthorities, roleAuthorities, permissionAuthorities)
                .flatMap(stream -> stream)
                .toList();
    }

    private Stream<String> nullSafe(List<String> values) {
        return values == null ? Stream.empty() : values.stream();
    }

    private List<String> claimAsList(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (value instanceof String text) {
            return List.of(text.split(" "));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }
}
