package pt.terrapi.core.config;

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

    /**
     * Slim mode. {@code false} (default) keeps the middle in RAM - faster, but needs several GB of
     * memory; best for dev and full reimports. {@code true} stores the middle on disk
     * ({@code --slim --drop --flat-nodes}) - memory-gentle for constrained hosts, but slower.
     */
    private boolean slim = false;

    /** Worker processes for osm2pgsql. 0 = auto (number of available CPU cores). */
    private int numberProcesses = 0;

    /** Throttle interval for progress log lines, in seconds (0 = log every progress line). */
    private int logProgressSeconds = 10;
}
