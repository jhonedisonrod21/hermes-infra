package co.com.hermes.calendar.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting básico, por IP, sobre los endpoints sensibles (login y registro) para frenar
 * fuerza bruta de credenciales y abuso de alta de cuentas.
 *
 * <p>Es una ventana fija <b>en memoria, por instancia</b>: eleva el listón frente a abuso sencillo
 * pero NO es un límite distribuido. Para escalado horizontal del gateway se requiere un limiter
 * compartido (p. ej. Redis + {@code RequestRateLimiter}). Documentado como requisito de infra.</p>
 */
@Component
public class SensitiveRouteRateLimitFilter implements GlobalFilter, Ordered {

    private record Rule(String pathPrefix, int maxRequests, long windowMillis) { }

    private static final class Window {
        private final long start;
        private final AtomicInteger count;

        private Window(long start) {
            this.start = start;
            this.count = new AtomicInteger(0);
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("/auth/session/login", 10, Duration.ofMinutes(1).toMillis()),
            new Rule("/identity/users/register", 5, Duration.ofMinutes(1).toMillis())
    );
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        Rule rule = ruleFor(path);
        if (rule == null) {
            return chain.filter(exchange);
        }

        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(window -> now - window.start >= rule.windowMillis());
        }

        String key = rule.pathPrefix() + '|' + clientIp(exchange.getRequest());
        Window window = windows.compute(key, (ignored, current) ->
                current == null || now - current.start >= rule.windowMillis() ? new Window(now) : current);

        if (window.count.incrementAndGet() > rule.maxRequests()) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private static Rule ruleFor(String path) {
        for (Rule rule : RULES) {
            if (path.startsWith(rule.pathPrefix())) {
                return rule;
            }
        }
        return null;
    }

    private static String clientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        // Antes de cualquier otra cosa (incluida la autenticación), para no gastar recursos.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
