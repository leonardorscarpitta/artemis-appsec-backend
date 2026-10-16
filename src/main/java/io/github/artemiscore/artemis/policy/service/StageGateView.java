package io.github.artemiscore.artemis.policy.service;

import io.github.artemiscore.artemis.shared.domain.FailThreshold;
import io.github.artemiscore.artemis.shared.domain.Stage;
import jakarta.validation.constraints.NotNull;

/** {@code failOn} is {@code null} for artifact-only stages. */
public record StageGateView(@NotNull Stage stage, FailThreshold failOn, boolean required) {
}
