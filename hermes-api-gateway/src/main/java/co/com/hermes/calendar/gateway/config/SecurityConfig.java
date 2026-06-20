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
                                "/webjars/**"
                        ).permitAll()
                        .pathMatchers("/*/internal/**", "/*/actuator/**").denyAll()
                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/bff/**").permitAll()
                        .pathMatchers("/identity/users/register").permitAll()
                        .pathMatchers("/identity/**").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers("/tenant/**").hasAnyAuthority("tenant:manage")
                        .pathMatchers("/catalog/**").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers("/scheduling/**").hasAnyAuthority("calendar:read", "calendar:write")
                        .pathMatchers("/payment/**").hasAnyAuthority("billing:read", "billing:write")
                        .pathMatchers("/notification/**").authenticated()
                        .pathMatchers("/integration/**").hasAnyRole("OWNER", "ADMIN")
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
