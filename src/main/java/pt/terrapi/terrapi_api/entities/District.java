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
@Table(name = "districts")
public class District extends BaseAdministrativeEntity {

    @OneToMany(mappedBy = "district")
    private List<Municipality> municipalities = new ArrayList<>();
}
