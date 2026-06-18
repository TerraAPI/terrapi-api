package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pt.terrapi.terrapi_api.enums.StatUnitLevel;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stat_units", indexes = {
        @Index(name = "idx_stat_units_level", columnList = "level"),
        @Index(name = "idx_stat_units_parent_code", columnList = "parent_code"),
        @Index(name = "idx_stat_units_name", columnList = "name")
})
public class StatUnit extends BaseGeoEntity {

    @Column(nullable = false)
    private StatUnitLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_code")
    private StatUnit parent;

    @OneToMany(mappedBy = "parent")
    private List<StatUnit> children = new ArrayList<>();
}
