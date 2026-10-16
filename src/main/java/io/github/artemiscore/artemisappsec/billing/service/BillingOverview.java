package io.github.artemiscore.artemisappsec.billing.service;

import java.time.Instant;
import java.util.List;

import io.github.artemiscore.artemisappsec.billing.domain.Feature;
import io.github.artemiscore.artemisappsec.billing.domain.Plan;

/**
 * @param enabled whether plan changes are accepted
 * @param entitlements features available with the current plan
 */
public record BillingOverview(boolean enabled, Plan currentPlan, Instant since, List<PlanView> plans,
		List<Feature> entitlements, UsageView usage) {
}
