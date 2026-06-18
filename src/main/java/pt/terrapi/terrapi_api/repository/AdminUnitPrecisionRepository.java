package pt.terrapi.terrapi_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.terrapi.terrapi_api.entities.AdminUnitPrecision;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

import java.util.List;

public interface AdminUnitPrecisionRepository extends JpaRepository<AdminUnitPrecision, Long> {

    @Modifying
    @Query("UPDATE AdminUnitPrecision p SET p.status = 'DEPRECATED' WHERE p.status = 'ACTIVE' AND p.type IN :types")
    void deprecateActiveByTypes(@Param("types") List<GeoUnitType> types);
}
