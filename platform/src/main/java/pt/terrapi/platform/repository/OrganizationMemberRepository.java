package pt.terrapi.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.platform.entities.AppUser;
import pt.terrapi.platform.entities.Organization;
import pt.terrapi.platform.entities.OrganizationMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByOrganizationAndAppUser(Organization organization, AppUser appUser);

    List<OrganizationMember> findByAppUser(AppUser appUser);

    List<OrganizationMember> findByOrganization(Organization organization);
}
