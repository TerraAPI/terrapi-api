package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import pt.terrapi.terrapi_api.enums.GenerationStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "precision_generations")
public class PrecisionGeneration {

    @Id
    @Column(name = "generation_id")
    private UUID generationId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private GenerationStatus status;

    @Column(name = "total_units")
    private int totalUnits;

    @Column(name = "total_lods")
    private int totalLods;

    @Column(name = "invalid_geometries")
    private int invalidGeometries;

    @Column(name = "null_geometries")
    private int nullGeometries;

    @Column(name = "row_count")
    private int rowCount;

    @PrePersist
    void setDefaults() {
        if (generationId == null) {
            generationId = UUID.randomUUID();
        }
    }

    public void updateCounters(int rowCount, int nullGeometries, int invalidGeometries,
                               int totalUnits, int totalLods) {
        this.rowCount = rowCount;
        this.nullGeometries = nullGeometries;
        this.invalidGeometries = invalidGeometries;
        this.totalUnits = totalUnits;
        this.totalLods = totalLods;
    }
}
