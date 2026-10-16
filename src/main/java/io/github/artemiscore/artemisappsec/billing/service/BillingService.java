package io.github.artemiscore.artemisappsec.billing.service;

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

	/** Throws when the organization's monthly scan limit has been reached. */
	void assertCanScan(UUID organizationId);
}
