package io.github.artemiscore.artemisappsec.billing.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.billing.domain.Feature;

/** Plans, the organization's subscription and the features it unlocks. */
public interface BillingService {

	BillingOverview overview();

	/** Public plan catalog (prices, limits and features). */
	List<PlanView> plans();

	BillingOverview changePlan(ChangePlanCommand command);

	/** Other modules use this to gate paid features (e.g. AI triage). */
	boolean isEntitled(Feature feature);

	/** Throws when the plan's monthly scan limit has been reached. */
	void assertCanScan();
}
