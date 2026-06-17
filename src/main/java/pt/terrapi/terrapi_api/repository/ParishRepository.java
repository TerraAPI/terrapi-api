package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.Parish;

public interface ParishRepository extends JpaRepository<Parish, Long> {
    Optional<Parish> findByCode(String code);
    List<Parish> findByMunicipalityId(Long municipalityId);
}
