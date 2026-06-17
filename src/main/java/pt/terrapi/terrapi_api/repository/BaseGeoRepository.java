package pt.terrapi.terrapi_api.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import pt.terrapi.terrapi_api.entities.BaseGeoEntity;

@NoRepositoryBean
public interface BaseGeoRepository<T extends BaseGeoEntity> extends JpaRepository<T, Long> {
    Optional<T> findByCode(String code);
}
