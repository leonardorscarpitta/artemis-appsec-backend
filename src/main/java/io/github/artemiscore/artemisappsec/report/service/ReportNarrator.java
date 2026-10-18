package io.github.artemiscore.artemisappsec.report.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationFeatures;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationSettingsService;
import io.github.artemiscore.artemisappsec.report.domain.Report;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.AiNotice;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.Narrative;
import io.github.artemiscore.artemisappsec.report.persistence.ReportRepository;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;

/**
 * Writes a report's summary and recommendations: with AI for organizations that have the AI feature (when an AI key
 * is configured and the daily limit is not reached), otherwise from the standard templates.
 */
@Component
class ReportNarrator {

	private final OrganizationFeatures features;
	private final OrganizationSettingsService settings;
	private final ClaudeClient claude;
	private final ReportRepository reports;
	private final int aiReportsPerDay;
	private final Clock clock = Clock.systemUTC();

	ReportNarrator(OrganizationFeatures features, OrganizationSettingsService settings, ClaudeClient claude,
			ReportRepository reports, ArtemisAppSecProperties properties) {
		this.features = features;
		this.settings = settings;
		this.claude = claude;
		this.reports = reports;
		this.aiReportsPerDay = properties.ai().reportsPerDay();
	}

	ReportContent write(Report report, ReportFacts facts) {
		if (!features.isEnabled(report.getOrganizationId(), Feature.AI_TRIAGE)) {
			return standard(facts, null);
		}
		if (!claude.configured()) {
			return standard(facts, AiNotice.UNAVAILABLE);
		}
		Instant today = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
		if (reports.countByOrganizationIdAndAiUsedTrueAndGeneratedAtGreaterThanEqual(report.getOrganizationId(), today)
				>= aiReportsPerDay) {
			return standard(facts, AiNotice.LIMIT);
		}
		// Only the organization's default language is written, to avoid paying for a translation nobody reads.
		String language = settings.get(report.getOrganizationId()).general().defaultLanguage();
		Optional<ClaudeClient.AiText> ai = claude.write(AiReportPayload.SYSTEM_INSTRUCTION,
				AiReportPayload.of(facts, Instant.now(clock), language));
		return ai.map(t -> with(facts, Map.of(language, t.summary()),
				t.recommendations().stream().map(r -> Map.of(language, r)).toList(), Narrative.AI, null))
			.orElseGet(() -> standard(facts, AiNotice.UNAVAILABLE));
	}

	private static ReportContent standard(ReportFacts facts, AiNotice notice) {
		return with(facts, ReportTexts.summary(facts), ReportTexts.recommendations(facts), Narrative.ARTEMIS, notice);
	}

	private static ReportContent with(ReportFacts facts, Map<String, String> summary,
			List<Map<String, String>> recommendations, Narrative narrative, AiNotice notice) {
		ReportContent c = facts.content();
		return new ReportContent(summary, c.kpis(), c.trend(), c.severity(), c.topRisks(), c.owasp(), recommendations,
				narrative, notice);
	}
}
