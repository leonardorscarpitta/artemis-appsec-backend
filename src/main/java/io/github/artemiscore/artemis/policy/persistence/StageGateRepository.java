package io.github.artemiscore.artemis.policy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.policy.domain.StageGate;
import io.github.artemiscore.artemis.shared.domain.Stage;

public interface StageGateRepository extends JpaRepository<StageGate, Stage> {
}
