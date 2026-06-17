package pt.terrapi.terrapi_api.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.Nuts1;

public interface Nuts1Repository extends JpaRepository<Nuts1, Long> {
    Optional<Nuts1> findByCode(String code);
    Optional<Nuts1> findByName(String name);
}
