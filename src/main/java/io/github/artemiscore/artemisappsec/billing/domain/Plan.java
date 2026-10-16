package io.github.artemiscore.artemisappsec.billing.domain;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;

/** Plan catalog. Prices are in cents of {@link #CURRENCY}, charged monthly. */
public enum Plan {
	FREE(0, Set.of()),
	PRO(2499, Set.of(Feature.AI_TRIAGE));

	public static final String CURRENCY = "BRL";

	private final long monthlyPriceCents;
	private final Set<Feature> features;

	Plan(long monthlyPriceCents, Set<Feature> features) {
		this.monthlyPriceCents = monthlyPriceCents;
		this.features = features;
	}

	public long monthlyPriceCents() {
		return monthlyPriceCents;
	}

	public Set<Feature> features() {
		return features;
	}

	public boolean isPaid() {
		return monthlyPriceCents > 0;
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
