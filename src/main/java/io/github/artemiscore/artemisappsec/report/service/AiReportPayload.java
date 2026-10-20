package io.github.artemiscore.artemisappsec.report.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.ComponentNote;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.ImageNote;
import io.github.artemiscore.artemisappsec.report.domain.ReportSection;
import io.github.artemiscore.artemisappsec.shared.ai.ClaudeClient;

/**
 * The only data Artemis sends to the AI provider to write a report: aggregated figures and the top risks described
 * by public identifiers (CVE or rule id, CWE, package and versions that look like identifiers; no titles for secrets
 * or licenses). It never contains source code, file paths, secret values or matches, free text taken from the
 * repository, repository or organization names, people, keys or logs. Keep it that way: this is what
 * the Privacy Policy describes.
 */
final class AiReportPayload {

	private static final int MAX_TITLE = 70;
	private static final int MAX_RISKS = 8;
	private static final Pattern LICENSE_LIKE = Pattern.compile("[A-Za-z0-9.+()\\- ]{1,64}");

	/** A small output cap keeps each report to roughly a thousand or two tokens. */
	static final long MAX_TOKENS = 1024;
	private static final int MAX_SUMMARY = 1200;
	private static final int MAX_RECOMMENDATION = 500;
	private static final int MAX_RECOMMENDATIONS = 6;

	private static final Map<String, Object> TEXT = Map.of("type", "string");
	private static final int MAX_NOTES = 8;
	private static final int MAX_NOTE = 240;
	private static final Map<String, Object> COMPONENTS = Map.of("type", "array", "items", Map.of("type", "object",
			"properties", Map.of("pkg", TEXT, "concern", Map.of("type", "string", "enum", ComponentNote.CONCERNS), "note", TEXT),
			"required", List.of("pkg", "concern", "note"), "additionalProperties", false));
	private static final Map<String, Object> IMAGES = Map.of("type", "array", "items", Map.of("type", "object",
			"properties", Map.of("ref", TEXT, "standing", Map.of("type", "string", "enum", ImageNote.STANDINGS), "note", TEXT),
			"required", List.of("ref", "standing", "note"), "additionalProperties", false));

	/** Whether the AI has anything to write for the report's topics. */
	static boolean needed(ReportFacts facts) {
		return wantsText(facts) || facts.content().dependencies() != null;
	}

	/** Only the requested topics are asked for: every output field costs tokens. */
	static Map<String, Object> schema(ReportFacts facts) {
		Map<String, Object> properties = new LinkedHashMap<>();
		if (facts.sections().contains(ReportSection.SUMMARY)) {
			properties.put("summary", TEXT);
		}
		if (facts.sections().contains(ReportSection.RECOMMENDATIONS)) {
			properties.put("recommendations", Map.of("type", "array", "items", TEXT));
		}
		if (facts.content().dependencies() != null) {
			properties.put("components", COMPONENTS);
			properties.put("images", IMAGES);
		}
		return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()),
				"additionalProperties", false);
	}

	static long maxTokens(ReportFacts facts) {
		return (wantsText(facts) ? MAX_TOKENS : 0) + (facts.content().dependencies() != null ? 640 : 0);
	}

	private static boolean wantsText(ReportFacts facts) {
		return facts.sections().contains(ReportSection.SUMMARY) || facts.sections().contains(ReportSection.RECOMMENDATIONS);
	}

	private AiReportPayload() {
	}

	/** Compact on purpose: every field costs tokens. */
	static Map<String, Object> of(ReportFacts facts, Instant now, String language) {
		ReportContent c = facts.content();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("lang", language);
		if (c.dependencies() != null) {
			payload.put("sbom", dependencies(c.dependencies(), facts.sbomPackages()));
		}
		if (!wantsText(facts)) {
			return payload;
		}
		payload.put("days", facts.periodDays());
		payload.put("detected", c.kpis().opened());
		payload.put("fixed", c.kpis().fixed());
		payload.put("open", c.kpis().open());
		payload.put("mttrDays", Math.round(c.kpis().mttrDays() * 10) / 10.0);
		payload.put("slaPct", c.kpis().slaCompliance());
		payload.put("pastSla", facts.overdue());
		payload.put("bySeverity", nonZero(c.severity()));
		payload.put("byKind", nonZero(facts.openByKind()));
		Map<String, Integer> owasp = new LinkedHashMap<>();
		c.owasp().stream().filter(o -> o.findings() > 0).forEach(o -> owasp.put(o.id(), o.findings()));
		payload.put("owasp", owasp);
		payload.put("risks", facts.risks().stream().limit(MAX_RISKS).map(f -> risk(f, now)).toList());
		return payload;
	}

	/**
	 * The SBOM topic: aggregate counts, SPDX license ids and only the vulnerable components (public package names
	 * with advisories), never the full component list or repository names.
	 */
	private static Map<String, Object> dependencies(ReportContent.Dependencies d, List<String> packages) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("repos", d.repositories().size());
		payload.put("reposWithoutSbom", d.withoutSbom().size());
		payload.put("components", d.components());
		payload.put("unique", d.unique());
		payload.put("noLicense", d.withoutLicense());
		Map<String, Integer> ecosystems = new LinkedHashMap<>();
		d.ecosystems().forEach((k, v) -> {
			if (LICENSE_LIKE.matcher(k).matches()) {
				ecosystems.put(k, v);
			}
		});
		payload.put("ecosystems", ecosystems);
		// License names come from package metadata: only identifier-like values (SPDX ids and expressions) are sent.
		Map<String, Integer> licenses = new LinkedHashMap<>();
		d.licenses().stream().filter(l -> LICENSE_LIKE.matcher(l.license()).matches())
			.forEach(l -> licenses.put(l.license(), l.components()));
		payload.put("licenses", licenses);
		payload.put("vulnerable", d.vulnerable());
		payload.put("vulnerableTop", d.vulnerableComponents().stream().limit(MAX_RISKS).map(v -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("pkg", ClaudeClient.packageRef(v.name(), v.version(), v.fixedVersion()));
			item.put("sev", v.severity());
			item.put("n", v.vulnerabilities());
			item.put("ids", v.ids());
			item.values().removeIf(x -> x == null);
			return item;
		}).toList());
		payload.put("images", images(d));
		payload.put("pkgs", packages);
		return payload;
	}

	/** Published container images (public registry references) of the repositories in the report. */
	private static List<String> images(ReportContent.Dependencies d) {
		return d.repositories().stream()
			.map(r -> ClaudeClient.safeIdentifier(r.image()))
			.filter(i -> i != null)
			.distinct()
			.toList();
	}

	private static Map<String, Object> risk(FindingSummary f, Instant now) {
		Map<String, Object> risk = new LinkedHashMap<>();
		risk.put("id", f.cve() != null ? f.cve() : f.ruleId());
		risk.put("kind", f.kind().wireValue());
		if (f.kind() != FindingKind.SECRET && f.kind() != FindingKind.LICENSE) {
			risk.put("title", truncate(f.title()));
		}
		risk.put("sev", f.severity().wireValue());
		risk.put("cvss", f.cvss());
		risk.put("cwe", f.cwe());
		if (f.packageName() != null) {
			risk.put("pkg", ClaudeClient.packageRef(f.packageName(), f.installedVersion(), f.fixedVersion()));
		}
		risk.put("ageDays", Duration.between(f.detectedAt(), now).toDays());
		risk.values().removeIf(v -> v == null);
		return risk;
	}

	private static <K> Map<K, ? extends Number> nonZero(Map<K, ? extends Number> counts) {
		Map<K, Number> out = new LinkedHashMap<>();
		counts.forEach((k, v) -> {
			if (v != null && v.doubleValue() != 0) {
				out.put(k, v);
			}
		});
		return out;
	}

	private static String truncate(String s) {
		return s == null || s.length() <= MAX_TITLE ? s : s.substring(0, MAX_TITLE);
	}

	static final String SYSTEM_INSTRUCTION = """
			AppSec analyst. From the JSON only (never invent CVEs, packages, versions or numbers), fill only the fields the output schema has:
			summary: executive summary, max 80 words (mention the SBOM when "sbom" is present);
			recommendations: 3-5, most urgent first, max 25 words each, citing ids, packages and fixed versions (pkg is name@installed→fixed).
			components: only sbom.pkgs entries (copy pkg exactly) that deserve attention: little-known publisher, low adoption, typosquat-like name, unmaintained or deprecated; max 8, note max 20 words; [] if none. Never flag well-known widely used packages.
			images: one per sbom.images entry (copy ref exactly): official, widely-used or unknown, note max 15 words.
			Write in the language of "lang" (en-US English, pt-BR Brazilian Portuguese). Plain text, no markdown.""";

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AiText(String summary, List<String> recommendations, List<AiComponent> components, List<AiImage> images) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AiComponent(String pkg, String concern, String note) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AiImage(String ref, String standing, String note) {
	}

	/** Keeps only notes about components and images that were sent, with known categories. */
	static List<ComponentNote> componentNotes(AiText text, ReportFacts facts) {
		Set<String> sent = Set.copyOf(facts.sbomPackages());
		return text.components() == null ? List.of() : text.components().stream()
			.filter(c -> c != null && sent.contains(c.pkg()) && ComponentNote.CONCERNS.contains(c.concern()))
			.map(c -> new ComponentNote(c.pkg(), c.concern(), ClaudeClient.clean(c.note(), MAX_NOTE)))
			.filter(c -> c.note() != null)
			.distinct()
			.limit(MAX_NOTES)
			.toList();
	}

	static List<ImageNote> imageNotes(AiText text, ReportFacts facts) {
		Set<String> sent = Set.copyOf(images(facts.content().dependencies()));
		return text.images() == null ? List.of() : text.images().stream()
			.filter(i -> i != null && sent.contains(i.ref()) && ImageNote.STANDINGS.contains(i.standing()))
			.map(i -> new ImageNote(i.ref(), i.standing(), ClaudeClient.clean(i.note(), MAX_NOTE)))
			.filter(i -> i.note() != null)
			.distinct()
			.limit(MAX_NOTES)
			.toList();
	}

	/** Checks the requested texts; topics that were not requested come back null. */
	static Optional<AiText> validated(AiText text, ReportFacts facts) {
		String summary = null;
		if (facts.sections().contains(ReportSection.SUMMARY)) {
			summary = ClaudeClient.clean(text.summary(), MAX_SUMMARY);
			if (summary == null) {
				return Optional.empty();
			}
		}
		List<String> recs = null;
		if (facts.sections().contains(ReportSection.RECOMMENDATIONS)) {
			if (text.recommendations() == null || text.recommendations().isEmpty()) {
				return Optional.empty();
			}
			recs = text.recommendations().stream()
				.limit(MAX_RECOMMENDATIONS)
				.map(r -> ClaudeClient.clean(r, MAX_RECOMMENDATION))
				.toList();
			if (recs.contains(null)) {
				return Optional.empty();
			}
		}
		return Optional.of(new AiText(summary, recs, text.components(), text.images()));
	}
}
