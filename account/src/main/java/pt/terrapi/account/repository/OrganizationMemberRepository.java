package pt.terrapi.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.account.entities.AppUser;
import pt.terrapi.account.entities.Organization;
import pt.terrapi.account.entities.OrganizationMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByOrganizationAndAppUser(Organization organization, AppUser appUser);

    List<OrganizationMember> findByAppUser(AppUser appUser);

    List<OrganizationMember> findByOrganization(Organization organization);
}
