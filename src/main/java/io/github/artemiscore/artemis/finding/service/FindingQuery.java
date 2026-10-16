package io.github.artemiscore.artemis.finding.service;

import io.github.artemiscore.artemis.finding.domain.FindingStatus;
import io.github.artemiscore.artemis.shared.domain.Severity;

public record FindingQuery(String text, Severity severity, FindingStatus status, String repository, SortField sort,
		boolean ascending) {

	public enum SortField {
		CVSS, DETECTED
	}
}
