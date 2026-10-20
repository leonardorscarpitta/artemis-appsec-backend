package io.github.artemiscore.artemisappsec.report.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import io.github.artemiscore.artemisappsec.shared.ai.AiUsageCounter;
import io.github.artemiscore.artemisappsec.shared.ai.ClaudeClient;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;

/**
 * Writes a report's summary and recommendations: with AI for organizations that have the AI feature (when an AI key
 * is configured, a call slot is free and the daily attempt limit is not reached), otherwise from the standard templates.
 */
@Component
class ReportNarrator {

	private final OrganizationFeatures features;
	private final OrganizationSettingsService settings;
	private final ClaudeClient claude;
	private final AiUsageCounter usage;
	private final int aiReportsPerDay;
	private final Clock clock = Clock.systemUTC();

	ReportNarrator(OrganizationFeatures features, OrganizationSettingsService settings, ClaudeClient claude,
			AiUsageCounter usage, ArtemisAppSecProperties properties) {
		this.features = features;
		this.settings = settings;
		this.claude = claude;
		this.usage = usage;
		this.aiReportsPerDay = properties.ai().reportsPerDay();
	}

	ReportContent write(Report report, ReportFacts facts) {
		if (!features.isEnabled(report.getOrganizationId(), Feature.AI_TRIAGE)) {
			return standard(facts, null);
		}
		if (!claude.configured()) {
			return standard(facts, AiNotice.UNAVAILABLE);
		}
		// Only the organization's default language is written, to avoid paying for a translation nobody reads.
		String language = settings.get(report.getOrganizationId()).general().defaultLanguage();
		if (!claude.tryAcquire(Duration.ofMinutes(2))) {
			return standard(facts, AiNotice.UNAVAILABLE);
		}
		Optional<AiReportPayload.AiText> ai;
		try {
			if (!usage.tryReserve(report.getOrganizationId(), LocalDate.now(clock), AiUsageCounter.REPORT, aiReportsPerDay)) {
				return standard(facts, AiNotice.LIMIT);
			}
			ai = claude
				.json(AiReportPayload.SYSTEM_INSTRUCTION, AiReportPayload.of(facts, Instant.now(clock), language),
						AiReportPayload.schema(facts), AiReportPayload.maxTokens(facts), AiReportPayload.AiText.class)
				.flatMap(AiReportPayload::validated);
		}
		finally {
			claude.release();
		}
		return ai.map(t -> with(facts, Map.of(language, t.summary()),
				t.recommendations().stream().map(r -> Map.of(language, r)).toList(), Narrative.AI, null,
				facts.content().dependencies() == null ? null : facts.content().dependencies()
					.withNotes(AiReportPayload.componentNotes(t, facts), AiReportPayload.imageNotes(t, facts))))
			.orElseGet(() -> standard(facts, AiNotice.UNAVAILABLE));
	}

	private static ReportContent standard(ReportFacts facts, AiNotice notice) {
		return with(facts, ReportTexts.summary(facts), ReportTexts.recommendations(facts), Narrative.ARTEMIS, notice,
				facts.content().dependencies());
	}

	private static ReportContent with(ReportFacts facts, Map<String, String> summary,
			List<Map<String, String>> recommendations, Narrative narrative, AiNotice notice,
			ReportContent.Dependencies dependencies) {
		ReportContent c = facts.content();
		return new ReportContent(summary, c.kpis(), c.trend(), c.severity(), c.topRisks(), c.owasp(), recommendations,
				narrative, notice, dependencies);
	}
}
