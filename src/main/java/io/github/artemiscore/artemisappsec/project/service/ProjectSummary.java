package io.github.artemiscore.artemisappsec.project.service;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.artemiscore.artemisappsec.project.domain.ScanSchedule;
import io.github.artemiscore.artemisappsec.project.domain.Stack;

/** {@code openFindings} is null when finding statistics are unavailable. */
public record ProjectSummary(UUID id, @JsonIgnore UUID organizationId, String name, String url, String branch, Stack stack, ScanSchedule schedule,
		Instant lastScanAt, Long openFindings) {
}
