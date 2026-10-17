package io.github.artemiscore.artemisappsec.organization.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemisappsec.organization.domain.Member;
import io.github.artemiscore.artemisappsec.organization.domain.Role;

public interface MemberRepository extends JpaRepository<Member, UUID> {

	List<Member> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

	Optional<Member> findByIdAndOrganizationId(UUID id, UUID organizationId);

	Optional<Member> findByOrganizationIdAndUserSubject(UUID organizationId, String userSubject);

	Optional<Member> findByOrganizationIdAndEmail(UUID organizationId, String email);

	/** Memberships of an account: already linked by subject, or not linked yet but with the same (verified) e-mail. */
	@Query("select m from Member m where m.userSubject = :subject or (m.userSubject is null and m.email = :email)")
	List<Member> findForAccount(String subject, String email);

	long countByUserSubjectAndRole(String userSubject, Role role);

	List<Member> findByUserSubjectAndRole(String userSubject, Role role);
}
