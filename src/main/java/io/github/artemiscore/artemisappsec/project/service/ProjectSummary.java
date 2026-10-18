package io.github.artemiscore.artemisappsec.project.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.artemiscore.artemisappsec.project.domain.AccessStatus;
import io.github.artemiscore.artemisappsec.project.domain.ScanSchedule;
import io.github.artemiscore.artemisappsec.project.domain.Stack;
import io.github.artemiscore.artemisappsec.project.domain.StackSource;

/**
 * {@code openFindings} is null when finding statistics are unavailable. {@code access} is {@code public} (HTTPS) or
 * {@code ssh} (private, with an Artemis deploy key); {@code accessStatus} is null for public repositories.
 * {@code stack} is null while {@code stackSource} is {@code pending} (automatic detection not done yet).
 * {@code nextScanAt} is set only for timed schedules; they run only when scheduled scans are enabled for the organization.
 * {@code removed}: the repository was removed but its history (findings, runs) was kept.
 */
public record ProjectSummary(UUID id, @JsonIgnore UUID organizationId, String name, String url, String branch, Stack stack, List<ProjectFolder> folders, @JsonIgnore String containerImage, String access,
		AccessStatus accessStatus, StackSource stackSource, ScanSchedule schedule,
		Instant nextScanAt, Instant lastScanAt, Long openFindings, @JsonIgnore boolean removed) {
}
