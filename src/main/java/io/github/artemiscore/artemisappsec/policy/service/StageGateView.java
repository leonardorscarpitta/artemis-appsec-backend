package io.github.artemiscore.artemisappsec.policy.service;

import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import jakarta.validation.constraints.NotNull;

/** {@code failOn} is {@code null} for artifact-only stages. */
public record StageGateView(@NotNull Stage stage, FailThreshold failOn, boolean required) {
}
