package io.github.artemiscore.artemisappsec.report.service;

import java.util.List;
import java.util.Map;

import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.report.domain.ReportType;

/**
 * Everything computed for one report before its texts are written. {@code content} has no summary or recommendations
 * yet; {@code risks} are the findings behind {@code content.topRisks()}.
 */
record ReportFacts(ReportType type, int periodDays, ReportContent content, List<FindingSummary> risks,
		Map<String, Long> openByKind, List<Upgrade> upgrades, long openSecrets, long overdue, long withoutFix) {

	/** Upgrading {@code packageName} to {@code fixedVersion} fixes {@code findings} open findings. */
	record Upgrade(String packageName, String fixedVersion, long findings, long critical, long high) {
	}
}
