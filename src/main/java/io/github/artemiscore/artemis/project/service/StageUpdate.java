package io.github.artemiscore.artemis.project.service;

import io.github.artemiscore.artemis.shared.domain.FailThreshold;
import io.github.artemiscore.artemis.shared.domain.Stage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StageUpdate(@NotNull Stage id, boolean enabled, @NotBlank String tool, FailThreshold failOn) {
}
