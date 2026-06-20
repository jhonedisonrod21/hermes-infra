package co.com.hermes.calendar.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GatewayIdentityHeaderFilter implements GlobalFilter, Ordered {

    private static final List<String> IDENTITY_HEADERS = List.of(
            "X-Hermes-User-Id",
            "X-Hermes-Username",
            "X-Hermes-Tenant-Id",
            "X-Hermes-Tenant-Slug",
            "X-Hermes-Roles",
            "X-Hermes-Permissions"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(removeIdentityHeaders(exchange.getRequest()))
                .build();

        return sanitizedExchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .flatMap(authentication -> chain.filter(withIdentityHeaders(sanitizedExchange, authentication)))
                .switchIfEmpty(chain.filter(sanitizedExchange));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    private ServerHttpRequest removeIdentityHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> IDENTITY_HEADERS.forEach(headers::remove))
                .build();
    }

    private ServerWebExchange withIdentityHeaders(
            ServerWebExchange exchange,
            JwtAuthenticationToken authentication
    ) {
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.set("X-Hermes-User-Id", authentication.getToken().getSubject());
                    setIfPresent(headers, "X-Hermes-Username", authentication.getToken().getClaimAsString("preferred_username"));
                    setIfPresent(headers, "X-Hermes-Tenant-Id", authentication.getToken().getClaimAsString("tenant_id"));
                    setIfPresent(headers, "X-Hermes-Tenant-Slug", authentication.getToken().getClaimAsString("tenant_slug"));
                    headers.set("X-Hermes-Roles", joinClaim(authentication, "roles"));
                    headers.set("X-Hermes-Permissions", joinClaim(authentication, "permissions"));
                })
                .build();

        return exchange.mutate().request(request).build();
    }

    private void setIfPresent(org.springframework.http.HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private String joinClaim(JwtAuthenticationToken authentication, String claim) {
        Object value = authentication.getToken().getClaims().get(claim);
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return value == null ? "" : String.valueOf(value);
    }
}
