package io.github.artemiscore.artemisappsec.finding.service;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;

public record FindingQuery(String text, FindingKind kind, Severity severity, FindingStatus status, String repository,
		SortField sort, boolean ascending) {

	public enum SortField {
		CVSS, DETECTED
	}
}
