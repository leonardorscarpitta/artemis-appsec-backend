package io.github.artemiscore.artemisappsec.policy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.policy.domain.StageGate;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

public interface StageGateRepository extends JpaRepository<StageGate, Stage> {
}
