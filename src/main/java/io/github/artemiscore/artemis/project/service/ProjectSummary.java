package io.github.artemiscore.artemis.project.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemis.project.domain.ScanSchedule;
import io.github.artemiscore.artemis.project.domain.Stack;

public record ProjectSummary(UUID id, String name, String url, String branch, Stack stack, ScanSchedule schedule,
		Instant lastScanAt) {
}
