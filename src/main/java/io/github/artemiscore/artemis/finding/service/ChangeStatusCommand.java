package io.github.artemiscore.artemis.finding.service;

import io.github.artemiscore.artemis.finding.domain.FindingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusCommand(@NotNull FindingStatus status, @Size(max = 1000) String note) {
}
