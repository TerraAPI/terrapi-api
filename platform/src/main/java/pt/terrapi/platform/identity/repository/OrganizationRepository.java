package pt.terrapi.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.platform.identity.entities.Organization;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
