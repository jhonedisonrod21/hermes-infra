package co.com.hermes.calendar.bff.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(
            HttpMethod method,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        String path = request.getRequestURI().replaceFirst("^/api", "");
        String query = request.getQueryString();
        String uri = StringUtils.hasText(query) ? path + "?" + query : path;

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
