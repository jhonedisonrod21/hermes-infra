package co.com.hermes.calendar.bff.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/session")
public class SessionController {

    private static final String REGISTRATION_ID = "hermes-web-client";

    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final WebClient gatewayWebClient;
    private final ObjectMapper objectMapper;

    public SessionController(OAuth2AuthorizedClientRepository authorizedClients,
                            WebClient gatewayWebClient, ObjectMapper objectMapper) {
        this.authorizedClients = authorizedClients;
        this.gatewayWebClient = gatewayWebClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public SessionUserResponse me(Authentication authentication, HttpServletRequest request) {
        if (!authenticated(authentication)) {
            return anonymous();
        }
        // Preferimos los claims del access token: reflejan el cambio de organización activa y persisten
        // entre recargas (el id_token siempre traería la organización por defecto).
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(REGISTRATION_ID, authentication, request);
        if (client != null && client.getAccessToken() != null) {
            Map<String, Object> claims = decodeJwt(client.getAccessToken().getTokenValue());
            if (!claims.isEmpty()) {
                return fromClaims(claims);
            }
        }
        if (authentication.getPrincipal() instanceof OidcUser user) {
            return fromClaims(user.getClaims());
        }
        return new SessionUserResponse(true, authentication.getName(), null, authentication.getName(),
                null, null, null, null, List.of(), List.of(), Map.of());
    }

    /**
     * Cambia la organización activa: pide al auth-server un token nuevo (con su bearer actual) y
     * <b>reemplaza el access token almacenado</b>, de modo que las llamadas siguientes por el proxy
     * usen los roles/permisos de la organización elegida. Devuelve la sesión ya actualizada.
     */
    @PostMapping("/switch-tenant")
    @ResponseStatus(HttpStatus.OK)
    public SessionUserResponse switchTenant(@RequestBody SwitchTenantRequest body, Authentication authentication,
                                            HttpServletRequest request, HttpServletResponse response) {
        if (!authenticated(authentication)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (body == null || body.tenantId() == null || body.tenantId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        OAuth2AuthorizedClient current = authorizedClients.loadAuthorizedClient(REGISTRATION_ID, authentication, request);
        if (current == null || current.getAccessToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No active session token");
        }

        Map<String, Object> switched;
        try {
            // El filtro OAuth2 del WebClient inyecta el bearer ACTUAL (antes del swap).
            switched = gatewayWebClient.post()
                    .uri("/auth/session/switch-tenant")
                    .bodyValue(Map.of("tenantId", body.tenantId()))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { })
                    .block();
        } catch (WebClientResponseException ex) {
            // Propaga el motivo real del auth-server (403 si no es miembro, 409 si es cuenta de plataforma…).
            throw new ResponseStatusException(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getResponseBodyAsString());
        } catch (OAuth2AuthorizationException _) {
            // El token actual expiró y no se pudo renovar: sesión caducada -> 401 (diálogo de reautenticación).
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
        }
        if (switched == null || switched.get("accessToken") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Switch did not return a token");
        }

        String newToken = String.valueOf(switched.get("accessToken"));
        long expiresIn = switched.get("expiresIn") instanceof Number n ? n.longValue() : 3600L;
        Instant now = Instant.now();
        OAuth2AccessToken newAccess = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, newToken,
                now, now.plusSeconds(expiresIn), current.getAccessToken().getScopes());
        OAuth2AuthorizedClient swapped = new OAuth2AuthorizedClient(current.getClientRegistration(),
                current.getPrincipalName(), newAccess, current.getRefreshToken());
        authorizedClients.saveAuthorizedClient(swapped, authentication, request, response);

        Map<String, Object> claims = decodeJwt(newToken);
        return !claims.isEmpty() ? fromClaims(claims) : me(authentication, request);
    }

    private static boolean authenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private SessionUserResponse fromClaims(Map<String, Object> claims) {
        return new SessionUserResponse(
                true,
                stringClaim(claims, "sub"),
                stringClaim(claims, "user_id"),
                stringClaim(claims, "preferred_username"),
                stringClaim(claims, "email"),
                stringClaim(claims, "tenant_id"),
                stringClaim(claims, "tenant_slug"),
                stringClaim(claims, "tenant_name"),
                listClaim(claims, "roles"),
                listClaim(claims, "permissions"),
                claims
        );
    }

    /** Decodifica el payload del JWT (sin verificar firma: es el token propio que guarda el BFF). */
    private Map<String, Object> decodeJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Map.of();
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() { });
        } catch (Exception _) {
            return Map.of();
        }
    }

    private static SessionUserResponse anonymous() {
        return new SessionUserResponse(false, null, null, null, null, null, null, null, List.of(), List.of(), Map.of());
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> listClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.split(" "));
        }
        return List.of();
    }
}
