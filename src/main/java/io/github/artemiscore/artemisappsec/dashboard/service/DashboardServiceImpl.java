package io.github.artemiscore.artemisappsec.dashboard.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.artemiscore.artemisappsec.dashboard.service.DashboardSummary.KindCount;
import io.github.artemiscore.artemisappsec.dashboard.service.DashboardSummary.RepositoryCount;
import io.github.artemiscore.artemisappsec.dashboard.service.DashboardSummary.SeverityCount;
import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.service.FindingService;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.finding.service.FindingTrend;
import io.github.artemiscore.artemisappsec.pipelinerun.service.PipelineRunService;
import io.github.artemiscore.artemisappsec.pipelinerun.service.RunActivity;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;

@Service
class DashboardServiceImpl implements DashboardService {

	private static final int RECENT_LIMIT = 8;
	private static final int TOP_REPOSITORIES = 5;

	private final FindingService findings;
	private final ProjectService projects;
	private final PipelineRunService runs;
	private final Clock clock = Clock.systemUTC();

	DashboardServiceImpl(FindingService findings, ProjectService projects, PipelineRunService runs) {
		this.findings = findings;
		this.projects = projects;
		this.runs = runs;
	}

	@Override
	public DashboardSummary summary(UUID organizationId) {
		List<FindingSummary> active = findings.findActive(organizationId);
		List<ProjectSummary> repositories = projects.findAll(organizationId);
		double average = active.stream().map(FindingSummary::cvss).filter(Objects::nonNull).mapToDouble(Double::doubleValue)
			.average().orElse(0);
		List<SeverityCount> bySeverity = Arrays.stream(Severity.values())
			.map(s -> new SeverityCount(s, active.stream().filter(f -> f.severity() == s).count()))
			.toList();
		List<KindCount> byKind = Arrays.stream(FindingKind.values())
			.map(k -> new KindCount(k, active.stream().filter(f -> f.kind() == k).count()))
			.toList();
		Instant lastScan = repositories.stream()
			.map(ProjectSummary::lastScanAt)
			.filter(Objects::nonNull)
			.max(Comparator.naturalOrder())
			.orElse(null);
		return new DashboardSummary(active.size(), countOf(bySeverity, Severity.CRITICAL), Math.round(average * 10) / 10.0,
				lastScan, repositories.size(), bySeverity, active.stream().limit(RECENT_LIMIT).toList(),
				active.stream().filter(FindingSummary::overdue).count(), byKind, topRepositories(active));
	}

	@Override
	public DashboardTrends trends(UUID organizationId, int days, ZoneId zone) {
		LocalDate to = LocalDate.now(clock.withZone(zone));
		LocalDate from = to.minusDays(days - 1L);
		FindingTrend trend = findings.trend(organizationId, from, to, zone);
		Map<LocalDate, RunActivity> activity = runs.activity(organizationId, from, zone).stream()
			.collect(Collectors.toMap(RunActivity::date, Function.identity()));
		List<DashboardTrends.Day> series = trend.days().stream().map(d -> {
			RunActivity a = activity.getOrDefault(d.date(), new RunActivity(d.date(), 0, 0, 0));
			return new DashboardTrends.Day(d.date(), d.critical(), d.high(), d.medium(), d.low(), d.detected(), d.fixed(),
					a.succeeded(), a.failed(), a.cancelled());
		}).toList();
		return new DashboardTrends(days, zone.getId(), series, trend.meanTimeToRemediateDays(), trend.fixedInPeriod());
	}

	private static List<RepositoryCount> topRepositories(List<FindingSummary> active) {
		return active.stream()
			.collect(Collectors.groupingBy(FindingSummary::repository))
			.entrySet().stream()
			.map(e -> new RepositoryCount(e.getKey(), e.getValue().size(), count(e.getValue(), Severity.CRITICAL),
					count(e.getValue(), Severity.HIGH), count(e.getValue(), Severity.MEDIUM), count(e.getValue(), Severity.LOW)))
			.sorted(Comparator.comparingLong(RepositoryCount::total).reversed()
				.thenComparing(Comparator.comparingLong(RepositoryCount::critical).reversed())
				.thenComparing(RepositoryCount::repository))
			.limit(TOP_REPOSITORIES)
			.toList();
	}

	private static long count(List<FindingSummary> findings, Severity severity) {
		return findings.stream().filter(f -> f.severity() == severity).count();
	}

	private static long countOf(List<SeverityCount> counts, Severity severity) {
		return counts.stream().filter(c -> c.severity() == severity).mapToLong(SeverityCount::count).sum();
	}
}
