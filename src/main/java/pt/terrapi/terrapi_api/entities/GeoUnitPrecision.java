package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "geo_unit_precisions", indexes = {
        @Index(name = "idx_gp_code_lod_status", columnList = "geo_unit_code, lod, status"),
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

    @Column(columnDefinition = "geometry(Geometry, 3857)", nullable = false)
    private Geometry geometry;

    @Column(name = "tolerance_m")
    private double toleranceM;

    @Column(name = "vertex_count")
    private int vertexCount;

    @Column(name = "generation_id")
    private UUID generationId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(length = 20)
    private String status = "ACTIVE";

    @PrePersist
    void setDefaults() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }
}
