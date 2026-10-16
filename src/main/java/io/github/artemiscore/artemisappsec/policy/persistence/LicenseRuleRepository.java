package io.github.artemiscore.artemisappsec.policy.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemisappsec.policy.domain.LicenseRule;

public interface LicenseRuleRepository extends JpaRepository<LicenseRule, UUID> {

	List<LicenseRule> findByOrganizationIdOrderByLicenseIdAsc(UUID organizationId);

	@Modifying(flushAutomatically = true)
	@Query("delete from LicenseRule r where r.organizationId = :organizationId")
	void deleteByOrganization(UUID organizationId);
}
