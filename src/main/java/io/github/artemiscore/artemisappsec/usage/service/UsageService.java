package io.github.artemiscore.artemisappsec.usage.service;

import java.time.LocalDate;
import java.util.UUID;

/** Each organization's monthly scan quota. */
public interface UsageService {

	/** Pipeline runs allowed per organization and calendar month (UTC). */
	int MONTHLY_SCAN_LIMIT = 25;

	UsageOverview overview(UUID organizationId);

	/**
	 * Counts one scan against the organization's monthly limit, or throws when it is reached. Runs in the caller's
	 * transaction, so a scan that is not queued after all does not use the quota.
	 *
	 * @return the month (first day, UTC) the scan was counted in, to give it back with {@link #refundScan}
	 */
	LocalDate consumeScan(UUID organizationId);

	/** Gives back a scan counted in {@code month}, for runs that failed before scanning anything. */
	void refundScan(UUID organizationId, LocalDate month);
}
