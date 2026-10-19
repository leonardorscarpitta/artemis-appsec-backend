package io.github.artemiscore.artemisappsec.insight.service;

import java.time.Instant;

/**
 * AI explanation of a finding in {@code language} (the organization's language when it was generated).
 * {@code priority} is {@code high}, {@code medium} or {@code low}.
 */
public record FindingInsightView(String impact, String exploitable, String priority, String fix, String language,
		String model, Instant createdAt) {
}
