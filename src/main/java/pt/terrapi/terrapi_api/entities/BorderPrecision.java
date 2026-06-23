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

import java.time.Instant;
import java.util.UUID;

/**
 * An LOD tier of a {@link BorderSegment}: the same classified boundary arc, independently
 * line-simplified ({@code ST_SimplifyPreserveTopology}) per LOD. Mirrors {@link GeoUnitPrecision}
 * for layer fills; carries the arc's edge-level semantics ({@code level}, {@code lineType}) so the
 * border overlay can be served at the same LOD ladder as the layers.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "border_segment_precisions", indexes = {
        @Index(name = "idx_bsp_level_lod", columnList = "level, lod"),
        @Index(name = "idx_bsp_generation_id", columnList = "generation_id")
})
public class BorderPrecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "border_segment_id", nullable = false)
    private Long borderSegmentId;

    /** Administrative order of the border: 1 (national/top) .. 5 (parish-level). */
    @Column(name = "level")
    private Integer level;

    /** LAND, COAST or WATER (from the source {@link BorderSegment}). */
    @Column(name = "line_type", length = 10)
    private String lineType;

    @Column(name = "length_km")
    private Double lengthKm;

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

    @PrePersist
    void setDefaults() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
