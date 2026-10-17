package io.github.artemiscore.artemisappsec.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.report.domain.Report;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.report.domain.ReportType;
import io.github.artemiscore.artemisappsec.report.persistence.ReportRepository;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class ReportServiceImpl implements ReportService {

	private static final String EN = "en-US";
	private static final String PT = "pt-BR";

	private static final Map<ReportType, Map<String, String>> TYPE_TITLES = Map.of(
			ReportType.SUMMARY, Map.of(EN, "Security summary", PT, "Resumo de segurança"),
			ReportType.COMPLIANCE, Map.of(EN, "OWASP Top 10 compliance", PT, "Conformidade OWASP Top 10"));

	private final ReportRepository reports;
	private final ProjectService projects;
	private final Clock clock;

	ReportServiceImpl(ReportRepository reports, ProjectService projects) {
		this.reports = reports;
		this.projects = projects;
		this.clock = Clock.systemUTC();
	}

	@Override
	public List<ReportSummary> findAll(UUID organizationId, Locale locale) {
		Map<UUID, String> names = projectNames(organizationId);
		return reports.findByOrganizationIdOrderByGeneratedAtDesc(organizationId).stream().map(r -> toSummary(r, names, locale)).toList();
	}

	@Override
	public ReportDetails get(UUID organizationId, UUID id, Locale locale) {
		Report report = reports.findByIdAndOrganizationId(id, organizationId)
			.orElseThrow(() -> new ResourceNotFoundException("Report", id));
		return new ReportDetails(toSummary(report, projectNames(organizationId), locale), toContent(report.getContent(), locale));
	}

	@Override
	@Transactional
	public ReportSummary request(UUID organizationId, GenerateReportCommand command, Locale locale) {
		String scope = command.scope() == null || command.scope().isBlank() ? null : command.scope();
		UUID projectId = scope == null ? null : projectId(organizationId, scope);
		LocalDate[] period = command.period().lastCompleteBefore(LocalDate.now(clock));
		Map<String, String> title = TYPE_TITLES.get(command.type()).entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, e -> scope == null ? e.getValue() : e.getValue() + " – " + scope));
		Report report = reports.save(Report.requested(organizationId, title, command.type(), projectId, period[0], period[1]));
		return toSummary(report, projectNames(organizationId), locale);
	}

	private UUID projectId(UUID organizationId, String name) {
		try {
			return projects.getByName(organizationId, name).project().id();
		}
		catch (ResourceNotFoundException ex) {
			throw new IllegalArgumentException("Unknown repository '%s'".formatted(name));
		}
	}

	/** Includes removed repositories, whose history is kept. */
	private Map<UUID, String> projectNames(UUID organizationId) {
		return projects.findAllIncludingRemoved(organizationId).stream().collect(Collectors.toMap(ProjectSummary::id, ProjectSummary::name));
	}

	private static ReportSummary toSummary(Report r, Map<UUID, String> names, Locale locale) {
		String scope = r.getProjectId() == null ? null : names.get(r.getProjectId());
		return new ReportSummary(r.getId(), localized(r.getTitle(), locale), r.getType(), scope, r.getPeriodStart(),
				r.getPeriodEnd(), r.getGeneratedAt(), r.getGeneratedBy(), r.getStatus());
	}

	private static ReportDetails.Content toContent(ReportContent c, Locale locale) {
		if (c == null) {
			return null;
		}
		Function<Map<String, String>, String> text = m -> localized(m, locale);
		return new ReportDetails.Content(text.apply(c.summary()), c.kpis(), c.trend(), c.severity(), c.topRisks(), c.owasp(),
				c.recommendations().stream().map(text).toList());
	}

	private static String localized(Map<String, String> texts, Locale locale) {
		String preferred = "pt".equals(locale.getLanguage()) ? PT : EN;
		return texts.getOrDefault(preferred, texts.get(EN));
	}
}
