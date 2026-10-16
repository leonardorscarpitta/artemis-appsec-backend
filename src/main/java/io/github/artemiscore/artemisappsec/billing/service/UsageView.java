package io.github.artemiscore.artemisappsec.billing.service;

import java.time.Instant;

/** Scans used in the current calendar month (UTC) against the plan's limit. */
public record UsageView(long scansThisMonth, int scanLimit, Instant periodStart) {
}
