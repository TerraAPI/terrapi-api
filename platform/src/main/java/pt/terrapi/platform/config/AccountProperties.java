package pt.terrapi.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("terrapi.account")
public record AccountProperties(
        @DefaultValue("tp_live_") String apiKeyPrefix
) {
}
