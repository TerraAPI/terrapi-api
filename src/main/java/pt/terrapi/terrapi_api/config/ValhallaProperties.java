package pt.terrapi.terrapi_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("terrapi.valhalla")
public class ValhallaProperties {

    /** Base URL of the Valhalla routing service. */
    private String url = "http://localhost:8002";
}
