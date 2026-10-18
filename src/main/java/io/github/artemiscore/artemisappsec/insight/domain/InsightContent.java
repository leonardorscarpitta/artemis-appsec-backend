package io.github.artemiscore.artemisappsec.insight.domain;

/** AI explanation of a finding, stored as JSON; {@code priority} is {@code high}, {@code medium} or {@code low}. */
public record InsightContent(String impact, String exploitable, String priority, String fix) {
}
