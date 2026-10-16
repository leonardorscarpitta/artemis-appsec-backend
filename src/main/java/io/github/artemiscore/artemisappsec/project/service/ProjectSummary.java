package io.github.artemiscore.artemisappsec.project.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.artemiscore.artemisappsec.project.domain.AccessStatus;
import io.github.artemiscore.artemisappsec.project.domain.ScanSchedule;
import io.github.artemiscore.artemisappsec.project.domain.Stack;

/**
 * {@code openFindings} is null when finding statistics are unavailable. {@code access} is {@code public} (HTTPS) or
 * {@code ssh} (private, with an Artemis deploy key); {@code accessStatus} is null for public repositories.
 */
public record ProjectSummary(UUID id, @JsonIgnore UUID organizationId, String name, String url, String branch, Stack stack, List<ProjectFolder> folders, @JsonIgnore String containerImage, String access,
		AccessStatus accessStatus, ScanSchedule schedule,
		Instant lastScanAt, Long openFindings) {
}
