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
import org.locationtech.jts.geom.Geometry;

/**
 * A boundary arc (CAOP {@code trocos}): a shared administrative border line with classification
 * and the codes of the units on each side.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "border_segments", indexes = {
        @Index(name = "idx_border_level", columnList = "level")
})
public class BorderSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "geometry(Geometry, 4326)")
    private Geometry geometry;

    /** {@link #geometry} pre-transformed to EPSG:3763, populated at import (used by precision gen). */
    @Column(name = "geometry_3763", columnDefinition = "geometry(Geometry, 3763)")
    private Geometry geometry3763;

    /** Administrative order of the border: 1 (national/top) .. 5 (parish-level). */
    @Column(name = "level")
    private Integer level;

    /** LAND, COAST or WATER (from {@code significado_linha}). */
    @Column(name = "line_type", length = 10)
    private String lineType;

    /** DICOFRE code of the unit on the right side (or a sea/foreign sentinel). */
    @Column(name = "ea_right", length = 8)
    private String eaRight;

    /** DICOFRE code of the unit on the left side (or a sea/foreign sentinel). */
    @Column(name = "ea_left", length = 8)
    private String eaLeft;

    @Column(name = "length_km")
    private Double lengthKm;
}
