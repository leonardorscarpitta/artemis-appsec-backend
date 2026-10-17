package io.github.artemiscore.artemisappsec.finding.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The same status change for several findings; {@code note}/{@code expiresAt} as in {@link ChangeStatusCommand}. */
public record BulkStatusCommand(@NotEmpty @Size(max = 500) List<@NotNull UUID> ids, @NotNull FindingStatus status,
		@Size(max = 1000) String note, LocalDate expiresAt) {
}
