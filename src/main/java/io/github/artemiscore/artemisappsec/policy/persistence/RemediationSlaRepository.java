package io.github.artemiscore.artemisappsec.policy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.policy.domain.RemediationSla;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;

public interface RemediationSlaRepository extends JpaRepository<RemediationSla, Severity> {
}
