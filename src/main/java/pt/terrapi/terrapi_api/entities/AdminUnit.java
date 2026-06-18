package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pt.terrapi.terrapi_api.enums.AdminUnitType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admin_units", indexes = {
        @Index(name = "idx_admin_units_type", columnList = "type"),
        @Index(name = "idx_admin_units_parent_code", columnList = "parent_code"),
        @Index(name = "idx_admin_units_name", columnList = "name")
})
public class AdminUnit extends BaseGeoEntity {

    @Column(nullable = false)
    private AdminUnitType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_code")
    private AdminUnit parent;

    @OneToMany(mappedBy = "parent")
    private List<AdminUnit> children = new ArrayList<>();

    private String simplifiedName;
}
