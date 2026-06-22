package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "geo_units", indexes = {
        @Index(name = "idx_geo_units_type", columnList = "type"),
        @Index(name = "idx_geo_units_parent_code", columnList = "parent_code"),
        @Index(name = "idx_geo_units_name", columnList = "name")
})
public class GeoUnit {

    @Id
    @Column(length = 6, nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "geometry(Geometry, 4326)")
    private Geometry geometry;

    private Double areaHa;

    private Double perimeterKm;

    @Column(nullable = false)
    private GeoUnitType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_code")
    private GeoUnit parent;

    @OneToMany(mappedBy = "parent")
    private List<GeoUnit> children = new ArrayList<>();

    /**
     * Simplified name - only populated for {@link GeoUnitType#PARISH} units.
     */
    private String simplifiedName;

    /**
     * NUTS 3 code - only populated for {@link GeoUnitType#MUNICIPALITY} units.
     */
    @Column(length = 5)
    private String nuts3Code;

    /**
     * Interior representative point (label/marker/fly-to), derived via ST_PointOnSurface.
     */
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point representativePoint;

    /**
     * Number of child municipalities (DISTRICT, ISLAND, NUTS levels); null for leaf types.
     */
    private Integer municipalityCount;

    /**
     * Number of child parishes (DISTRICT, ISLAND, MUNICIPALITY, NUTS levels); null for parishes.
     */
    private Integer parishCount;
}
