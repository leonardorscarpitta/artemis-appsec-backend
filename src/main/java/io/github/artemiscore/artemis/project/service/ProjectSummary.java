package io.github.artemiscore.artemis.project.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemis.project.domain.ScanSchedule;
import io.github.artemiscore.artemis.project.domain.Stack;

/** {@code openFindings} is null when finding statistics are unavailable. */
public record ProjectSummary(UUID id, String name, String url, String branch, Stack stack, ScanSchedule schedule,
		Instant lastScanAt, Long openFindings) {
}
