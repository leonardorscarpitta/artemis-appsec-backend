package io.github.artemiscore.artemisappsec.report.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.finding.service.FindingQuery;
import io.github.artemiscore.artemisappsec.finding.service.FindingService;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.report.domain.Report;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;

/**
 * Computes a report's figures from the organization's findings. Detections and fixes are counted in the report
 * period (UTC days); open counts, severities, top risks and OWASP categories describe the findings open now, leaving
 * out removed repositories. A fix is dated by the finding's last status change.
 */
@Component
class ReportBuilder {

	static final int TOP_RISKS = 15;
	private static final int UPGRADES = 3;

	private final FindingService findings;
	private final ProjectService projects;
	private final Clock clock = Clock.systemUTC();

	ReportBuilder(FindingService findings, ProjectService projects) {
		this.findings = findings;
		this.projects = projects;
	}

	ReportFacts build(Report report) {
		String repository = report.getProjectId() == null ? null
				: projects.findAllIncludingRemoved(report.getOrganizationId()).stream()
					.filter(p -> p.id().equals(report.getProjectId()))
					.map(ProjectSummary::name)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("Repository of the report no longer exists"));
		List<FindingSummary> all = findings.search(report.getOrganizationId(),
				new FindingQuery(null, null, null, null, repository, FindingQuery.SortField.DETECTED, true));

		Instant from = report.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant to = report.getPeriodEnd().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant now = Instant.now(clock);
		Predicate<Instant> inPeriod = t -> t != null && !t.isBefore(from) && t.isBefore(to);

		List<FindingSummary> opened = all.stream().filter(f -> inPeriod.test(f.detectedAt())).toList();
		List<FindingSummary> fixed = all.stream()
			.filter(f -> f.status() == FindingStatus.FIXED && inPeriod.test(f.statusChangedAt()))
			.toList();
		List<FindingSummary> open = all.stream().filter(f -> f.status().isActive() && !f.repositoryRemoved()).toList();

		double mttr = fixed.stream()
			.mapToLong(f -> Duration.between(f.detectedAt(), f.statusChangedAt()).toSeconds())
			.average()
			.stream()
			.map(s -> Math.round(s / 86_400 * 10) / 10.0)
			.findFirst()
			.orElse(0);
		ReportContent.Kpis kpis = new ReportContent.Kpis(opened.size(), fixed.size(), open.size(), mttr,
				slaCompliance(open, fixed));

		List<FindingSummary> risks = open.stream()
			.filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.HIGH)
			.sorted(Comparator.comparingDouble(ReportBuilder::score).reversed()
				.thenComparing(FindingSummary::detectedAt))
			.limit(TOP_RISKS)
			.toList();

		ReportContent content = new ReportContent(Map.of(), kpis, trend(report.getPeriodStart(), report.getPeriodEnd(),
				opened, fixed), severity(open), risks.stream().map(f -> topRisk(f, now)).toList(), owasp(open), List.of(),
				null, null);
		Map<String, Long> byKind = open.stream()
			.collect(Collectors.groupingBy(f -> f.kind().wireValue(), LinkedHashMap::new, Collectors.counting()));
		int periodDays = (int) ChronoUnit.DAYS.between(report.getPeriodStart(), report.getPeriodEnd()) + 1;
		return new ReportFacts(report.getType(), periodDays, content, risks, byKind, upgrades(open),
				open.stream().filter(f -> f.kind() == FindingKind.SECRET).count(),
				open.stream().filter(FindingSummary::overdue).count(),
				open.stream().filter(f -> f.kind() == FindingKind.VULNERABILITY && blank(f.fixedVersion())).count());
	}

	/** Share of findings with an SLA that are open within it or were fixed in the period before their deadline. */
	private static int slaCompliance(List<FindingSummary> open, List<FindingSummary> fixed) {
		long total = 0;
		long onTime = 0;
		for (FindingSummary f : open) {
			if (f.dueAt() != null) {
				total++;
				onTime += f.overdue() ? 0 : 1;
			}
		}
		for (FindingSummary f : fixed) {
			if (f.dueAt() != null) {
				total++;
				onTime += f.statusChangedAt().isAfter(f.dueAt()) ? 0 : 1;
			}
		}
		return total == 0 ? 100 : (int) Math.round(onTime * 100.0 / total);
	}

	/** 7-day blocks from the start of the period, labelled with the ISO week of their first day. */
	private static List<ReportContent.TrendPoint> trend(LocalDate start, LocalDate end, List<FindingSummary> opened,
			List<FindingSummary> fixed) {
		List<ReportContent.TrendPoint> points = new ArrayList<>();
		for (LocalDate block = start; !block.isAfter(end); block = block.plusDays(7)) {
			LocalDate blockStart = block;
			LocalDate blockEnd = block.plusDays(7);
			Predicate<Instant> inBlock = t -> {
				LocalDate day = LocalDate.ofInstant(t, ZoneOffset.UTC);
				return !day.isBefore(blockStart) && day.isBefore(blockEnd) && !day.isAfter(end);
			};
			points.add(new ReportContent.TrendPoint("W" + block.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
					(int) opened.stream().filter(f -> inBlock.test(f.detectedAt())).count(),
					(int) fixed.stream().filter(f -> inBlock.test(f.statusChangedAt())).count()));
		}
		return points;
	}

	private static Map<String, Integer> severity(List<FindingSummary> open) {
		Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
		for (Severity s : Severity.values()) {
			counts.put(s, 0);
		}
		open.forEach(f -> counts.merge(f.severity(), 1, Integer::sum));
		Map<String, Integer> result = new LinkedHashMap<>();
		counts.forEach((s, n) -> result.put(s.wireValue(), n));
		return result;
	}

	private static List<ReportContent.OwaspCategory> owasp(List<FindingSummary> open) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		OwaspTop10.ids().forEach(id -> counts.put(id, 0));
		open.forEach(f -> counts.merge(OwaspTop10.categoryOf(f.kind(), f.cwe()), 1, Integer::sum));
		return counts.entrySet().stream()
			.filter(e -> !OwaspTop10.OTHER.equals(e.getKey()) || e.getValue() > 0)
			.map(e -> new ReportContent.OwaspCategory(e.getKey(), OwaspTop10.name(e.getKey()), e.getValue()))
			.toList();
	}

	private static List<ReportFacts.Upgrade> upgrades(List<FindingSummary> open) {
		record Key(String packageName, String fixedVersion) {
		}
		Map<Key, List<FindingSummary>> groups = open.stream()
			.filter(f -> f.kind() == FindingKind.VULNERABILITY && !blank(f.packageName()) && !blank(f.fixedVersion()))
			.collect(Collectors.groupingBy(f -> new Key(f.packageName(), f.fixedVersion())));
		return groups.entrySet().stream()
			.map(e -> new ReportFacts.Upgrade(e.getKey().packageName(), e.getKey().fixedVersion(), e.getValue().size(),
					e.getValue().stream().filter(f -> f.severity() == Severity.CRITICAL).count(),
					e.getValue().stream().filter(f -> f.severity() == Severity.HIGH).count()))
			.sorted(Comparator.comparingLong(ReportFacts.Upgrade::critical)
				.thenComparingLong(ReportFacts.Upgrade::high)
				.thenComparingLong(ReportFacts.Upgrade::findings)
				.reversed())
			.limit(UPGRADES)
			.toList();
	}

	private static ReportContent.TopRisk topRisk(FindingSummary f, Instant now) {
		return new ReportContent.TopRisk(f.cve() != null ? f.cve() : f.ruleId() != null ? f.ruleId() : f.reference(),
				f.repository(), f.cvss(),
				(int) Duration.between(f.detectedAt(), now).toDays(), f.title(), f.severity().wireValue(),
				f.kind().wireValue(), f.packageName(), f.fixedVersion());
	}

	/** CVSS when known, otherwise the bottom of the severity's CVSS range. */
	private static double score(FindingSummary f) {
		if (f.cvss() != null) {
			return f.cvss();
		}
		return f.severity() == Severity.CRITICAL ? 9.0 : 7.0;
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
