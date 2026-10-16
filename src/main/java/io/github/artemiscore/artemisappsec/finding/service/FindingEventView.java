package io.github.artemiscore.artemisappsec.finding.service;

import java.time.Instant;

import io.github.artemiscore.artemisappsec.finding.domain.FindingEventType;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;

public record FindingEventView(FindingEventType type, FindingStatus status, String note, Instant occurredAt) {
}
