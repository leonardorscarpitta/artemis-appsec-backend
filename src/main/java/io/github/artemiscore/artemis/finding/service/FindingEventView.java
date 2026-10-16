package io.github.artemiscore.artemis.finding.service;

import java.time.Instant;

import io.github.artemiscore.artemis.finding.domain.FindingEventType;
import io.github.artemiscore.artemis.finding.domain.FindingStatus;

public record FindingEventView(FindingEventType type, FindingStatus status, String note, Instant occurredAt) {
}
