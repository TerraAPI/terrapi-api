package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;
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

    private String simplifiedName;

    @Column(length = 5)
    private String nuts3Code;
}
