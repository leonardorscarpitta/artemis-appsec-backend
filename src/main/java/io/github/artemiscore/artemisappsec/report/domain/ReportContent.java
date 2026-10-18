package io.github.artemiscore.artemisappsec.report.domain;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Generated report body, stored as JSON. Free text is localized: maps are keyed by language tag
 * ({@code en-US}, {@code pt-BR}). {@code narrative} tells who wrote the summary and recommendations (null in reports
 * created before generation existed); {@code aiNotice} says why AI text was not used although the organization has it.
 */
public record ReportContent(Map<String, String> summary, Kpis kpis, List<TrendPoint> trend,
		Map<String, Integer> severity, List<TopRisk> topRisks, List<OwaspCategory> owasp,
		List<Map<String, String>> recommendations, Narrative narrative, AiNotice aiNotice) {

	public record Kpis(int opened, int fixed, int open, double mttrDays, int slaCompliance) {
	}

	public record TrendPoint(String week, int opened, int fixed) {
	}

	/** {@code cve} holds the CVE, or the rule id for findings without one; {@code cvss} is null for findings without a score. */
	public record TopRisk(String cve, String repository, Double cvss, int ageDays, String title, String severity,
			String kind, String packageName, String fixedVersion) {
	}

	public record OwaspCategory(String id, String name, int findings) {
	}

	public enum Narrative {
		AI, ARTEMIS;

		@JsonValue
		public String wireValue() {
			return name().toLowerCase();
		}
	}

	public enum AiNotice {
		/** The AI provider failed or answered something unusable. */
		UNAVAILABLE,
		/** The organization reached its daily number of AI-written reports. */
		LIMIT;

		@JsonValue
		public String wireValue() {
			return name().toLowerCase();
		}
	}
}
