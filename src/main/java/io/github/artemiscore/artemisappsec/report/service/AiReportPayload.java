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

	private static final int MAX_TITLE = 160;

	private AiReportPayload() {
	}

	static Map<String, Object> of(ReportFacts facts, Instant now) {
		ReportContent c = facts.content();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("reportType", facts.type().code());
		payload.put("periodDays", facts.periodDays());
		payload.put("kpis", Map.of(
				"detectedInPeriod", c.kpis().opened(),
				"fixedInPeriod", c.kpis().fixed(),
				"openNow", c.kpis().open(),
				"meanTimeToRemediateDays", c.kpis().mttrDays(),
				"slaCompliancePercent", c.kpis().slaCompliance()));
		payload.put("openBySeverity", c.severity());
		payload.put("openByKind", facts.openByKind());
		payload.put("openPastSla", facts.overdue());
		payload.put("owaspTop10_2021", c.owasp().stream()
			.map(o -> Map.of("id", o.id(), "name", o.name(), "openFindings", o.findings()))
			.toList());
		payload.put("topRisks", facts.risks().stream().map(f -> risk(f, now)).toList());
		return payload;
	}

	private static Map<String, Object> risk(FindingSummary f, Instant now) {
		boolean secret = f.kind() == FindingKind.SECRET;
		Map<String, Object> risk = new LinkedHashMap<>();
		risk.put("id", f.cve() != null ? f.cve() : f.ruleId());
		risk.put("kind", f.kind().wireValue());
		risk.put("title", secret ? null : truncate(f.title()));
		risk.put("severity", f.severity().wireValue());
		risk.put("cvss", f.cvss());
		risk.put("cwe", f.cwe());
		risk.put("package", f.packageName());
		risk.put("installedVersion", f.installedVersion());
		risk.put("fixedVersion", f.fixedVersion());
		risk.put("ageDays", Duration.between(f.detectedAt(), now).toDays());
		risk.values().removeIf(v -> v == null);
		return risk;
	}

	private static String truncate(String s) {
		return s == null || s.length() <= MAX_TITLE ? s : s.substring(0, MAX_TITLE);
	}

	static final String SYSTEM_INSTRUCTION = """
			You are an application security analyst writing a security report for the engineering team and its managers.
			Use only the data provided in the user message (JSON). Never invent CVEs, packages, versions or numbers, and do not \
			mention repositories, people or tools that are not in the data.
			Write:
			- summary: a concise executive summary of at most 120 words;
			- recommendations: 3 to 6 prioritized, actionable recommendations, most urgent first, each one or two sentences, \
			referencing the CVE or rule ids, packages and fixed versions from the data when relevant.
			For a "compliance" report, frame the summary and recommendations by OWASP Top 10 2021 category.
			Write every text in English (field "en") and in Brazilian Portuguese (field "pt") with the same meaning.
			Plain text only: no markdown, no bullet characters, no headings.""";
}
