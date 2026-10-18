package io.github.artemiscore.artemisappsec.insight.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.github.artemiscore.artemisappsec.finding.service.FindingDetails;
import io.github.artemiscore.artemisappsec.finding.service.FindingService;
import io.github.artemiscore.artemisappsec.insight.domain.FindingInsight;
import io.github.artemiscore.artemisappsec.insight.domain.InsightContent;
import io.github.artemiscore.artemisappsec.insight.persistence.FindingInsightRepository;
import io.github.artemiscore.artemisappsec.organization.domain.Feature;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationFeatures;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationSettingsService;
import io.github.artemiscore.artemisappsec.project.service.ProjectFolder;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.ai.ClaudeClient;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

@Service
class FindingInsightServiceImpl implements FindingInsightService {

	private final FindingService findings;
	private final ProjectService projects;
	private final OrganizationFeatures features;
	private final OrganizationSettingsService settings;
	private final ClaudeClient claude;
	private final FindingInsightRepository insights;
	private final int insightsPerDay;
	private final Clock clock = Clock.systemUTC();

	FindingInsightServiceImpl(FindingService findings, ProjectService projects, OrganizationFeatures features,
			OrganizationSettingsService settings, ClaudeClient claude, FindingInsightRepository insights,
			ArtemisAppSecProperties properties) {
		this.findings = findings;
		this.projects = projects;
		this.features = features;
		this.settings = settings;
		this.claude = claude;
		this.insights = insights;
		this.insightsPerDay = properties.ai().insightsPerDay();
	}

	@Override
	public Optional<FindingInsightView> find(UUID organizationId, UUID findingId) {
		String key = InsightPayload.cacheKey(findings.get(organizationId, findingId).finding());
		return insights.findByOrganizationIdAndCacheKeyAndLanguage(organizationId, key, language(organizationId))
			.or(() -> insights.findFirstByOrganizationIdAndCacheKeyOrderByCreatedAtDesc(organizationId, key))
			.map(FindingInsightServiceImpl::toView);
	}

	@Override
	public FindingInsightView generate(UUID organizationId, UUID findingId) {
		if (!features.isEnabled(organizationId, Feature.AI_TRIAGE)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "feature-not-enabled",
					"AI triage is not enabled for this organization; ask the Artemis administrators",
					Map.of("feature", Feature.AI_TRIAGE.code()));
		}
		FindingDetails details = findings.get(organizationId, findingId);
		String key = InsightPayload.cacheKey(details.finding());
		// Only the organization's language is written, to avoid paying for a translation nobody reads.
		String language = language(organizationId);
		Optional<FindingInsight> cached = insights.findByOrganizationIdAndCacheKeyAndLanguage(organizationId, key, language);
		if (cached.isPresent()) {
			return toView(cached.get());
		}
		if (!claude.configured()) {
			throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "ai-unavailable",
					"AI is not configured on this Artemis server; ask the Artemis administrators");
		}
		Instant today = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
		if (insights.countByOrganizationIdAndCreatedAtGreaterThanEqual(organizationId, today) >= insightsPerDay) {
			throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "ai-limit",
					"Your organization reached today’s limit of %d AI explanations; try again tomorrow (UTC)"
						.formatted(insightsPerDay),
					Map.of("limit", insightsPerDay));
		}
		InsightContent content = claude
			.json(InsightPayload.SYSTEM_INSTRUCTION,
					InsightPayload.of(details, stack(organizationId, details.finding().repository()), language),
					InsightPayload.RESPONSE_SCHEMA, InsightPayload.MAX_TOKENS, InsightPayload.Answer.class)
			.flatMap(InsightPayload::validated)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.BAD_GATEWAY, "ai-failed",
					"The AI explanation could not be generated; try again later"));
		try {
			return toView(insights.saveAndFlush(
					new FindingInsight(organizationId, key, language, content, claude.model(), Instant.now(clock))));
		}
		catch (DataIntegrityViolationException e) {
			// Someone else generated it at the same time: keep theirs.
			return insights.findByOrganizationIdAndCacheKeyAndLanguage(organizationId, key, language)
				.map(FindingInsightServiceImpl::toView)
				.orElseThrow(() -> e);
		}
	}

	private String language(UUID organizationId) {
		return settings.get(organizationId).general().defaultLanguage();
	}

	/** "Java + Spring", or the folder stacks of a monorepo ("Angular, Java + Spring"); null while not detected. */
	private String stack(UUID organizationId, String repository) {
		return projects.findAllIncludingRemoved(organizationId).stream()
			.filter(p -> p.name().equals(repository))
			.findFirst()
			.map(FindingInsightServiceImpl::stackLabel)
			.orElse(null);
	}

	private static String stackLabel(ProjectSummary p) {
		if (p.folders() != null && p.folders().size() > 1) {
			return p.folders().stream().map(ProjectFolder::stack).distinct().map(s -> s.label())
				.collect(Collectors.joining(", "));
		}
		return p.stack() == null ? null : p.stack().label();
	}

	private static FindingInsightView toView(FindingInsight i) {
		InsightContent c = i.getContent();
		return new FindingInsightView(c.impact(), c.exploitable(), c.priority(), c.fix(), i.getLanguage(), i.getModel(),
				i.getCreatedAt());
	}
}
