package pt.terrapi.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.account.entities.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByKeycloakSub(String keycloakSub);
}
