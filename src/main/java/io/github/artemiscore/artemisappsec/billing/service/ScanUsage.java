package io.github.artemiscore.artemisappsec.billing.service;

import java.time.Instant;

/** Port implemented by the module that runs scans, so billing can count usage without depending on it. */
public interface ScanUsage {

	long scansSince(Instant since);
}
