package co.com.hermes.calendar.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "hermes.web")
public record WebBffProperties(
        String frontendUrl,
        List<String> allowedOrigins
) {
}
