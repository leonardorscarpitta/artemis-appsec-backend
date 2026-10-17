package io.github.artemiscore.artemisappsec.billing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.billing.domain.Feature;

/** Plans, each organization's subscription and the features it unlocks. */
public interface BillingService {

	BillingOverview overview(UUID organizationId);

	/** Public plan catalog (prices, limits and features). */
	List<PlanView> plans();

	BillingOverview changePlan(UUID organizationId, ChangePlanCommand command);

	/** Other modules use this to gate paid features (e.g. AI triage). */
	boolean isEntitled(UUID organizationId, Feature feature);

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
