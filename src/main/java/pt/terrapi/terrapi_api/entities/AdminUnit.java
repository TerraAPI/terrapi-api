package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import pt.terrapi.terrapi_api.enums.AdminUnitType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admin_units")
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
