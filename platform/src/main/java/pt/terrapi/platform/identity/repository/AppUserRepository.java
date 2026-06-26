package pt.terrapi.platform.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.platform.identity.entities.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByKeycloakSub(String keycloakSub);
}
