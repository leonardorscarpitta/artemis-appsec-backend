package io.github.artemiscore.artemisappsec.billing.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.billing.domain.Feature;
import io.github.artemiscore.artemisappsec.billing.domain.Plan;

public record PlanView(Plan id, long monthlyPriceCents, String currency, List<Feature> features) {

	static PlanView of(Plan plan) {
		return new PlanView(plan, plan.monthlyPriceCents(), Plan.CURRENCY, plan.features().stream().sorted().toList());
	}
}
