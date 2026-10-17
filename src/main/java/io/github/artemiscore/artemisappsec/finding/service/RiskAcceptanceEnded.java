package io.github.artemiscore.artemisappsec.finding.service;

import java.util.UUID;

/** Published when an accepted finding leaves the accepted status; its risk acceptance is removed. */
public record RiskAcceptanceEnded(UUID findingId) {
}
