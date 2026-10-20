package io.github.artemiscore.artemisappsec.report.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.report.domain.ReportSection;

/**
 * Everything computed for one report before its texts are written. {@code content} has no summary or recommendations
 * yet; {@code risks} are the findings behind {@code content.topRisks()}. {@code sbomPackages} (dependency reports
 * only) are public-registry components as {@code ecosystem:name@version}, vulnerable ones first, for the AI review.
 */
record ReportFacts(Set<ReportSection> sections, int periodDays, ReportContent content, List<FindingSummary> risks,
		Map<String, Long> openByKind, List<Upgrade> upgrades, long openSecrets, long overdue, long withoutFix,
		List<String> sbomPackages) {

	/** Upgrading {@code packageName} to {@code fixedVersion} fixes {@code findings} open findings. */
	record Upgrade(String packageName, String fixedVersion, long findings, long critical, long high) {
	}
}
