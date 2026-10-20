package io.github.artemiscore.artemisappsec.report.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Generated report body, stored as JSON. Free text is localized: maps are keyed by language tag
 * ({@code en-US}, {@code pt-BR}). {@code narrative} tells who wrote the summary and recommendations (null in reports
 * created before generation existed); {@code aiNotice} says why AI text was not used although the organization has it.
 * {@code dependencies} is only set in dependency (SBOM) reports.
 */
public record ReportContent(Map<String, String> summary, Kpis kpis, List<TrendPoint> trend,
		Map<String, Integer> severity, List<TopRisk> topRisks, List<OwaspCategory> owasp,
		List<Map<String, String>> recommendations, Narrative narrative, AiNotice aiNotice, Dependencies dependencies) {

	/**
	 * Built from the SBOM of each repository's most recent run that produced one. {@code unique} counts distinct
	 * name@version pairs across repositories; {@code withoutSbom} lists connected repositories that have no SBOM yet.
	 * {@code componentNotes} and {@code imageNotes} are the AI supply-chain assessment (null without AI).
	 */
	public record Dependencies(int components, int unique, int vulnerable, int withoutLicense,
			Map<String, Integer> ecosystems, List<LicenseCount> licenses, List<DependencyRepository> repositories,
			List<VulnerableComponent> vulnerableComponents, List<String> withoutSbom, List<ComponentNote> componentNotes,
			List<ImageNote> imageNotes) {

		public Dependencies withNotes(List<ComponentNote> components, List<ImageNote> images) {
			return new Dependencies(this.components, unique, vulnerable, withoutLicense, ecosystems, licenses, repositories,
					vulnerableComponents, withoutSbom, components, images);
		}
	}

	/** {@code image} is the published container image scanned by the image stage, or null. */
	public record DependencyRepository(String repository, long runNumber, Instant scannedAt, List<String> folders,
			int components, int vulnerable, int withoutLicense, String image) {
	}

	/** A component the AI flags: {@code concern} is one of {@link ComponentNote#CONCERNS}. */
	public record ComponentNote(String component, String concern, String note) {
		public static final List<String> CONCERNS = List.of("unknown-publisher", "low-adoption", "typosquat-risk",
				"unmaintained", "deprecated", "license", "other");
	}

	/** The AI view of a container image: {@code standing} is one of {@link ImageNote#STANDINGS}. */
	public record ImageNote(String image, String standing, String note) {
		public static final List<String> STANDINGS = List.of("official", "widely-used", "unknown");
	}

	public record LicenseCount(String license, int components) {
	}

	/** An SBOM component with open vulnerabilities; {@code ids} holds up to three CVE or advisory ids. */
	public record VulnerableComponent(String repository, String name, String version, String fixedVersion,
			String severity, int vulnerabilities, List<String> ids) {
	}

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
