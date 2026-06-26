package pt.terrapi.core.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.locationtech.jts.geom.LineString;

/**
 * Read-only view of a routable OSM highway edge in the {@code routing_edges} table.
 *
 * <p>The table is created and owned by osm2pgsql (see {@code osm/routing-edges.lua}); this entity
 * only maps it for querying, hence {@link Immutable} вЂ” the application never writes edges. Column
 * types mirror the Lua style exactly so Hibernate ({@code ddl-auto: update}) never alters the table.
 * No index is declared on {@code geom}; osm2pgsql owns the GIST index.
 */
@Getter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "routing_edges")
public class RoutingEdge {

    @Id
    @Column(name = "osm_id")
    private Long osmId;

    @Column(nullable = false, columnDefinition = "text")
    private String highway;

    @Column(columnDefinition = "text")
    private String oneway;

    @Column(columnDefinition = "text")
    private String junction;

    @Column(name = "maxspeed_kmh", columnDefinition = "int2")
    private Short maxspeedKmh;

    @Column(columnDefinition = "text")
    private String access;

    @Column(columnDefinition = "text")
    private String foot;

    @Column(columnDefinition = "text")
    private String bicycle;

    @Column(name = "motor_vehicle", columnDefinition = "text")
    private String motorVehicle;

    @Column(columnDefinition = "text")
    private String motorcar;

    @Column(columnDefinition = "text")
    private String service;

    @Column(columnDefinition = "text")
    private String surface;

    @Column(columnDefinition = "int2")
    private Short layer;

    private Boolean bridge;

    private Boolean tunnel;

    @Column(nullable = false, columnDefinition = "geometry(LineString,4326)")
    private LineString geom;
}
