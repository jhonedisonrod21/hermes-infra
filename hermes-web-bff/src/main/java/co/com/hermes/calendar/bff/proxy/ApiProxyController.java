package co.com.hermes.calendar.bff.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class ApiProxyController {

    private final WebClient gatewayWebClient;

    public ApiProxyController(WebClient gatewayWebClient) {
        this.gatewayWebClient = gatewayWebClient;
    }

    @RequestMapping(value = "/api/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<byte[]> proxy(
            HttpMethod method,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        String path = request.getRequestURI().replaceFirst("^/api", "");
        String query = request.getQueryString();
        String uri = StringUtils.hasText(query) ? path + "?" + query : path;

        try {
            return gatewayWebClient
                    .method(method)
                    .uri(uri)
                    .headers(headers -> copyHeaders(request, headers))
                    .body(body == null ? BodyInserters.empty() : BodyInserters.fromValue(body))
                    .exchangeToMono(response -> response
                            .toEntity(byte[].class)
                            .flatMap(entity -> Mono.just(ResponseEntity
                                    .status(entity.getStatusCode())
                                    .headers(entity.getHeaders())
                                    .body(entity.getBody()))))
                    .block();
        } catch (OAuth2AuthorizationException _) {
            // El access token del usuario expiró y no se pudo renovar (refresh inválido/expirado o el
            // auth-server falló al re-emitirlo). Es una expiración de sesión: devolvemos 401 para que el
            // SPA muestre el diálogo de reautenticación, en vez de un 500 tratado como error genérico.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private static void copyHeaders(HttpServletRequest request, HttpHeaders headers) {
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!HttpHeaders.HOST.equalsIgnoreCase(name)
                    && !HttpHeaders.COOKIE.equalsIgnoreCase(name)
                    && !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                    && !HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                request.getHeaders(name).asIterator().forEachRemaining(value -> headers.add(name, value));
            }
        });
    }
}
