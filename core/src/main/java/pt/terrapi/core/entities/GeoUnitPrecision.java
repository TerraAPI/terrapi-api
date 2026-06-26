package pt.terrapi.core.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Geometry;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "geo_unit_precisions", indexes = {
        @Index(name = "idx_gp_code_lod", columnList = "geo_unit_code, lod"),
        @Index(name = "idx_gp_generation_id", columnList = "generation_id")
})
public class GeoUnitPrecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "geo_unit_code", length = 6, nullable = false)
    private String geoUnitCode;

    @Column(nullable = false)
    private GeoUnitType type;

    @Column(nullable = false)
    private int lod;

    @Column(columnDefinition = "geometry(Geometry, 4326)", nullable = false)
    private Geometry geometry;

    @Column(name = "tolerance_m")
    private double toleranceM;

    @Column(name = "vertex_count")
    private int vertexCount;

    @Column(name = "generation_id")
    private UUID generationId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
