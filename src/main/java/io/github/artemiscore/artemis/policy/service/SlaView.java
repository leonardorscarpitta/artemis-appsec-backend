package io.github.artemiscore.artemis.policy.service;

import io.github.artemiscore.artemis.shared.domain.Severity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SlaView(@NotNull Severity severity, @Min(1) @Max(365) int days) {
}
