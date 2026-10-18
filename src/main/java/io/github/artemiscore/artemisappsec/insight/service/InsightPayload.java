package io.github.artemiscore.artemisappsec.insight.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.service.FindingDetails;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.insight.domain.InsightContent;
import io.github.artemiscore.artemisappsec.shared.ai.ClaudeClient;

/**
 * The only data Artemis sends to the AI provider to explain a finding: public identifiers (CVE or rule id, CWE,
 * package and versions), the scanner's title and description (neither for secrets), severity, CVSS and the
 * repository's stack. It never contains source code, file paths or lines, secret values, repository or organization
 * names, people or logs. Keep it that way: this is what the Privacy Policy describes.
 */
final class InsightPayload {

	private static final int MAX_TITLE = 100;
	private static final int MAX_DESCRIPTION = 400;
	/** Roughly 250-350 input and 150-250 output tokens per explanation. */
	static final long MAX_TOKENS = 400;

	private static final int MAX_IMPACT = 400;
	private static final int MAX_EXPLOITABLE = 300;
	private static final int MAX_FIX = 250;
	private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");

	private static final Map<String, Object> TEXT = Map.of("type", "string");
	static final Map<String, Object> RESPONSE_SCHEMA = Map.of("type", "object",
			"properties", Map.of("impact", TEXT, "exploitable", TEXT,
					"priority", Map.of("type", "string", "enum", List.of("high", "medium", "low")), "fix", TEXT),
			"required", List.of("impact", "exploitable", "priority", "fix"), "additionalProperties", false);

	static final String SYSTEM_INSTRUCTION = """
			AppSec analyst. Explain one finding from the JSON only (never invent CVEs, packages, versions or facts):
			impact: what an attacker could do to this kind of system (stack), in confidentiality/integrity/availability terms, max 40 words;
			exploitable: conditions needed to exploit it (use vector when given), max 30 words;
			priority: high, medium or low urgency to fix;
			fix: one concrete action, e.g. upgrade the package to the fixed version (pkg is name@installed→fixed), max 25 words.
			Write in the language of "lang" (en-US English, pt-BR Brazilian Portuguese). Plain text, no markdown.""";

	private InsightPayload() {
	}

	/**
	 * Same key, same answer: a dependency vulnerability by CVE (or rule id), ecosystem, package and installed version;
	 * other kinds by rule id and CWE.
	 */
	static String cacheKey(FindingSummary f) {
		String id = f.cve() != null ? f.cve() : f.ruleId();
		if (f.packageName() != null) {
			return String.join("|", f.kind().wireValue(), nz(id), f.ecosystem() == null ? "" : f.ecosystem().name(),
					f.packageGroup() == null ? f.packageName() : f.packageGroup() + ":" + f.packageName(),
					nz(f.installedVersion()));
		}
		return String.join("|", f.kind().wireValue(), nz(id), nz(f.cwe()));
	}

	/** Compact on purpose: every field costs tokens. */
	static Map<String, Object> of(FindingDetails details, String stack, String language) {
		FindingSummary f = details.finding();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("lang", language);
		payload.put("kind", f.kind().wireValue());
		payload.put("id", f.cve() != null ? f.cve() : f.ruleId());
		// Secrets are described by their rule id only.
		if (f.kind() != FindingKind.SECRET) {
			payload.put("title", truncate(f.title(), MAX_TITLE));
			payload.put("desc", truncate(details.description(), MAX_DESCRIPTION));
		}
		payload.put("sev", f.severity().wireValue());
		payload.put("cvss", f.cvss());
		payload.put("vector", details.vector());
		payload.put("cwe", details.cwe() != null ? details.cwe() : f.cwe());
		if (f.packageName() != null) {
			payload.put("pkg", f.packageName() + (f.installedVersion() == null ? "" : "@" + f.installedVersion())
					+ (f.fixedVersion() == null ? "" : "→" + f.fixedVersion()));
			payload.put("eco", f.ecosystem() == null ? null : f.ecosystem().name().toLowerCase());
		}
		payload.put("stack", stack);
		payload.values().removeIf(v -> v == null || "".equals(v));
		return payload;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Answer(String impact, String exploitable, String priority, String fix) {
	}

	static Optional<InsightContent> validated(Answer a) {
		String impact = ClaudeClient.clean(a.impact(), MAX_IMPACT);
		String exploitable = ClaudeClient.clean(a.exploitable(), MAX_EXPLOITABLE);
		String fix = ClaudeClient.clean(a.fix(), MAX_FIX);
		if (impact == null || exploitable == null || fix == null || !PRIORITIES.contains(a.priority())) {
			return Optional.empty();
		}
		return Optional.of(new InsightContent(impact, exploitable, a.priority(), fix));
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		String t = s.strip().replaceAll("\\s+", " ");
		return t.length() <= max ? t : t.substring(0, max);
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
