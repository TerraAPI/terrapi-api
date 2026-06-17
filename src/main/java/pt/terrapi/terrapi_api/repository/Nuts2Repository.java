package pt.terrapi.terrapi_api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.Nuts2;

public interface Nuts2Repository extends JpaRepository<Nuts2, Long> {
    Optional<Nuts2> findByCode(String code);
    Optional<Nuts2> findByName(String name);
    List<Nuts2> findByParentId(Long parentId);
}
