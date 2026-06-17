package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.Nuts3;

public interface Nuts3Repository extends JpaRepository<Nuts3, Long> {
    Optional<Nuts3> findByCode(String code);
    List<Nuts3> findByParentId(Long parentId);
}
