package pt.terrapi.terrapi_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("terrapi.osm")
public class OsmProperties {

    /** Path to the osm2pgsql executable (defaults to the binary on PATH). */
    private String osm2pgsqlPath = "osm2pgsql";

    /** Classpath location of the flex Lua style. */
    private String styleResource = "osm/routing-edges.lua";

    /** osm2pgsql node cache size in MB (kept small to stay gentle on the running server). */
    private int cacheMb = 1024;
}
