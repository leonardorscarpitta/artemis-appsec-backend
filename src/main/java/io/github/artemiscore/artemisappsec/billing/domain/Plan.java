package io.github.artemiscore.artemisappsec.billing.domain;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;

/** Plan catalog. Prices are in cents of {@link #CURRENCY}, charged monthly; scans are pipeline runs per month. */
public enum Plan {
	FREE(0, 90, Set.of()),
	PRO(2499, 300, Set.of(Feature.AI_TRIAGE));

	public static final String CURRENCY = "BRL";

	private final long monthlyPriceCents;
	private final int monthlyScanLimit;
	private final Set<Feature> features;

	Plan(long monthlyPriceCents, int monthlyScanLimit, Set<Feature> features) {
		this.monthlyPriceCents = monthlyPriceCents;
		this.monthlyScanLimit = monthlyScanLimit;
		this.features = features;
	}

	/** Pipeline runs allowed per calendar month (UTC). */
	public int monthlyScanLimit() {
		return monthlyScanLimit;
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
