package io.github.artemiscore.artemis.policy.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.policy.domain.LicenseRule;

public interface LicenseRuleRepository extends JpaRepository<LicenseRule, String> {

	List<LicenseRule> findAllByOrderByLicenseIdAsc();
}
