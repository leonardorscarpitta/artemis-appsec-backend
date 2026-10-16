package io.github.artemiscore.artemisappsec.project.service;

import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StageUpdate(@NotNull Stage id, boolean enabled, @NotBlank String tool, FailThreshold failOn) {
}
