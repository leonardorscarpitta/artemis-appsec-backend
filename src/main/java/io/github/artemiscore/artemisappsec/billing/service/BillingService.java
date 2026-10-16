package io.github.artemiscore.artemisappsec.billing.service;

import io.github.artemiscore.artemisappsec.billing.domain.Feature;

/** Plans, the organization's subscription and the features it unlocks. */
public interface BillingService {

	BillingOverview overview();

	BillingOverview changePlan(ChangePlanCommand command);

	/** Other modules use this to gate paid features (e.g. AI triage). */
	boolean isEntitled(Feature feature);
}
