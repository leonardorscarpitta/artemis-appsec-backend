package io.github.artemiscore.artemis.policy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.policy.domain.RemediationSla;
import io.github.artemiscore.artemis.shared.domain.Severity;

public interface RemediationSlaRepository extends JpaRepository<RemediationSla, Severity> {
}
