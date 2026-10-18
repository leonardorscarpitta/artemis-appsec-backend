package io.github.artemiscore.artemisappsec.report.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;

/**
 * The only data Artemis sends to the AI provider to write a report: aggregated figures and the top risks described
 * by public identifiers (CVE or rule id, CWE, package and versions). It never contains source code, file paths,
 * secret values or matches, repository or organization names, people, keys or logs. Keep it that way: this is what
 * the Privacy Policy describes.
 */
final class AiReportPayload {

	private static final int MAX_TITLE = 70;
	private static final int MAX_RISKS = 8;

	private AiReportPayload() {
	}

	/** Compact on purpose: every field costs tokens. */
	static Map<String, Object> of(ReportFacts facts, Instant now) {
		ReportContent c = facts.content();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", facts.type().code());
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

	private static Map<String, Object> risk(FindingSummary f, Instant now) {
		Map<String, Object> risk = new LinkedHashMap<>();
		risk.put("id", f.cve() != null ? f.cve() : f.ruleId());
		risk.put("kind", f.kind().wireValue());
		if (f.kind() != FindingKind.SECRET) {
			risk.put("title", truncate(f.title()));
		}
		risk.put("sev", f.severity().wireValue());
		risk.put("cvss", f.cvss());
		risk.put("cwe", f.cwe());
		if (f.packageName() != null) {
			risk.put("pkg", f.packageName() + (f.installedVersion() == null ? "" : "@" + f.installedVersion())
					+ (f.fixedVersion() == null ? "" : "→" + f.fixedVersion()));
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
			AppSec analyst. From the JSON only (never invent CVEs, packages, versions or numbers), write:
			summary: executive summary, max 80 words;
			recommendations: 3-5, most urgent first, max 25 words each, citing ids, packages and fixed versions (pkg is name@installed→fixed).
			Type "compliance": frame by OWASP 2021 category (owasp = category id → open findings).
			Each text in English ("en") and Brazilian Portuguese ("pt"). Plain text, no markdown.""";
}
