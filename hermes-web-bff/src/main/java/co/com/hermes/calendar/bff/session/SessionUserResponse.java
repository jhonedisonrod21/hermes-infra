package co.com.hermes.calendar.bff.session;

import java.util.List;
import java.util.Map;

public record SessionUserResponse(
        boolean authenticated,
        String sub,
        String userId,
        String preferredUsername,
        String email,
        String tenantId,
        String tenantSlug,
        String tenantName,
        List<String> roles,
        List<String> permissions,
        Map<String, Object> claims
) {
}
