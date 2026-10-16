package io.github.artemiscore.artemisappsec.report.domain;

import java.util.List;
import java.util.Map;

/**
 * Generated report body, stored as JSON. Free text is localized: maps are keyed by language tag
 * ({@code en-US}, {@code pt-BR}).
 */
public record ReportContent(Map<String, String> summary, Kpis kpis, List<TrendPoint> trend,
		Map<String, Integer> severity, List<TopRisk> topRisks, List<OwaspCategory> owasp,
		List<Map<String, String>> recommendations) {

	public record Kpis(int opened, int fixed, int open, double mttrDays, int slaCompliance) {
	}

	public record TrendPoint(String week, int opened, int fixed) {
	}

	public record TopRisk(String cve, String repository, double cvss, int ageDays) {
	}

	public record OwaspCategory(String id, String name, int findings) {
	}
}
