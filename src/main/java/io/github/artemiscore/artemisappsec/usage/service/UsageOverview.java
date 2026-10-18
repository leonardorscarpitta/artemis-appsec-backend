package io.github.artemiscore.artemisappsec.usage.service;

import java.time.Instant;
import java.util.List;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;

/**
 * Scans used in the current calendar month (UTC) and the optional features enabled for the organization.
 *
 * @param resetsAt start of the next month (UTC), when the counter starts again
 */
public record UsageOverview(long scansThisMonth, int scanLimit, Instant periodStart, Instant resetsAt, List<Feature> features) {
}
