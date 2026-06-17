package pt.terrapi.terrapi_api.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "nuts1")
public class Nuts1 extends BaseGeoEntity {

    @OneToMany(mappedBy = "parent")
    private List<Nuts2> children = new ArrayList<>();
}
