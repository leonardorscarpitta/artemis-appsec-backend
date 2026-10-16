package io.github.artemiscore.artemisappsec.finding.service;

import java.util.List;

/** {@code related} lists the same CVE found in other repositories or packages. */
public record FindingDetails(FindingSummary finding, String description, String vector, String cwe,
		List<FindingEventView> timeline, List<FindingSummary> related) {
}
