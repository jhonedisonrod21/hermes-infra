package co.com.hermes.calendar.bff.session;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/session")
public class SessionController {

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public SessionUserResponse me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return anonymous();
        }

        if (authentication.getPrincipal() instanceof OidcUser user) {
            Map<String, Object> claims = user.getClaims();
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

        return new SessionUserResponse(
                true,
                authentication.getName(),
                null,
                authentication.getName(),
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private static SessionUserResponse anonymous() {
        return new SessionUserResponse(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                Map.of()
        );
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
