package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
@Table(name = "stat_units")
public class StatUnit extends BaseGeoEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatUnitLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_code")
    private StatUnit parent;

    @OneToMany(mappedBy = "parent")
    private List<StatUnit> children = new ArrayList<>();
}
