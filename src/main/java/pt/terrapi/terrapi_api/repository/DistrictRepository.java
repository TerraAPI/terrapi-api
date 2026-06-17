package pt.terrapi.terrapi_api.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.District;

public interface DistrictRepository extends JpaRepository<District, Long> {
    Optional<District> findByCode(String code);
    Optional<District> findByName(String name);
}
