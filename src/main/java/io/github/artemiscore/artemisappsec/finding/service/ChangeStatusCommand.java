package io.github.artemiscore.artemisappsec.finding.service;

import java.time.LocalDate;

import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param note reason for the change; required (at least 10 characters) when accepting a risk, as its justification
 * @param expiresAt when an accepted risk ends (required for {@code accepted}, at most one year ahead)
 */
public record ChangeStatusCommand(@NotNull FindingStatus status, @Size(max = 1000) String note, LocalDate expiresAt) {

	public ChangeStatusCommand(FindingStatus status, String note) {
		this(status, note, null);
	}
}
