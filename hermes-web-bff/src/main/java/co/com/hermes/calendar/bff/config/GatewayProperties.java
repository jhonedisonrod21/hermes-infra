package co.com.hermes.calendar.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hermes.gateway")
public record GatewayProperties(String baseUrl) {
}
