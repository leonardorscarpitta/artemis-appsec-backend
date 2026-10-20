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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.finding.service.FindingQuery;
import io.github.artemiscore.artemisappsec.finding.service.FindingService;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.pipelinerun.service.LatestSbom;
import io.github.artemiscore.artemisappsec.pipelinerun.service.PipelineRunService;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.report.domain.Report;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.report.domain.ReportType;
import io.github.artemiscore.artemisappsec.shared.ai.ClaudeClient;
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
	private static final int LICENSES = 10;
	private static final int VULNERABLE_COMPONENTS = 25;
	private static final int IDS_PER_COMPONENT = 3;
	/** Components listed for the AI supply-chain review: about 12 tokens each. */
	private static final int AI_PACKAGES = 200;
	private static final Set<String> PUBLIC_ECOSYSTEMS = Set.of("npm", "maven", "pypi", "golang", "cargo", "gem",
			"nuget", "composer", "deb", "apk", "rpm", "hex", "pub", "cocoapods", "swift", "conan", "github");

	private final FindingService findings;
	private final ProjectService projects;
	private final PipelineRunService runs;
	private final Clock clock = Clock.systemUTC();

	ReportBuilder(FindingService findings, ProjectService projects, PipelineRunService runs) {
		this.findings = findings;
		this.projects = projects;
		this.runs = runs;
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

		List<String> sbomPackages = new ArrayList<>();
		ReportContent content = new ReportContent(Map.of(), kpis, trend(report.getPeriodStart(), report.getPeriodEnd(),
				opened, fixed), severity(open), risks.stream().map(f -> topRisk(f, now)).toList(), owasp(open), List.of(),
				null, null, report.getType() == ReportType.DEPENDENCIES ? dependencies(report, repository, open, sbomPackages) : null);
		Map<String, Long> byKind = open.stream()
			.collect(Collectors.groupingBy(f -> f.kind().wireValue(), LinkedHashMap::new, Collectors.counting()));
		int periodDays = (int) ChronoUnit.DAYS.between(report.getPeriodStart(), report.getPeriodEnd()) + 1;
		return new ReportFacts(report.getType(), periodDays, content, risks, byKind, upgrades(open),
				open.stream().filter(f -> f.kind() == FindingKind.SECRET).count(),
				open.stream().filter(FindingSummary::overdue).count(),
				open.stream().filter(f -> f.kind() == FindingKind.VULNERABILITY && blank(f.fixedVersion())).count(),
				sbomPackages);
	}

	/**
	 * Components of each connected repository's latest SBOM; a component is vulnerable when an open vulnerability
	 * finding of the same repository names its package and version.
	 */
	private ReportContent.Dependencies dependencies(Report report, String repository, List<FindingSummary> open,
			List<String> sbomPackages) {
		List<LatestSbom> sboms = runs.latestSboms(report.getOrganizationId(), repository);
		Map<String, List<FindingSummary>> vulnerabilities = open.stream()
			.filter(f -> f.kind() == FindingKind.VULNERABILITY && !blank(f.packageName()) && !blank(f.installedVersion()))
			.collect(Collectors.groupingBy(f -> componentKey(f.repository(), f.packageName(), f.installedVersion())));

		Map<String, List<LatestSbom>> byRepository = sboms.stream()
			.collect(Collectors.groupingBy(LatestSbom::repository, LinkedHashMap::new, Collectors.toList()));
		Set<String> unique = new HashSet<>();
		Map<String, Integer> ecosystems = new LinkedHashMap<>();
		Map<String, Integer> licenses = new LinkedHashMap<>();
		List<ReportContent.DependencyRepository> repositories = new ArrayList<>();
		List<ReportContent.VulnerableComponent> vulnerable = new ArrayList<>();
		Set<String> vulnerablePackages = new TreeSet<>();
		Set<String> otherPackages = new TreeSet<>();
		Map<String, String> images = projects.findAll(report.getOrganizationId()).stream()
			.filter(p -> p.containerImage() != null && !p.containerImage().isBlank())
			.collect(Collectors.toMap(ProjectSummary::name, ProjectSummary::containerImage, (a, b) -> a));
		int total = 0;
		int withoutLicense = 0;
		for (Map.Entry<String, List<LatestSbom>> entry : byRepository.entrySet()) {
			String name = entry.getKey();
			Set<String> seen = new HashSet<>();
			int repoComponents = 0;
			int repoVulnerable = 0;
			int repoWithoutLicense = 0;
			for (LatestSbom sbom : entry.getValue()) {
				for (SbomDigest.Component c : SbomDigest.components(sbom.gzipped())) {
					String version = c.version() == null ? "" : c.version();
					// Monorepo folders may list the same component: count it once per repository.
					if (!seen.add(c.ecosystem() + "|" + c.group() + "|" + c.name() + "|" + version)) {
						continue;
					}
					repoComponents++;
					unique.add(c.ecosystem() + "|" + c.group() + "|" + c.name() + "|" + version);
					ecosystems.merge(c.ecosystem(), 1, Integer::sum);
					if (c.license() == null) {
						repoWithoutLicense++;
					}
					else {
						licenses.merge(c.license(), 1, Integer::sum);
					}
					List<FindingSummary> matched = new ArrayList<>(vulnerabilities.getOrDefault(componentKey(name, c.name(), version), List.of()));
					if (c.group() != null) {
						matched.addAll(vulnerabilities.getOrDefault(componentKey(name, c.group() + ":" + c.name(), version), List.of()));
					}
					String ref = PUBLIC_ECOSYSTEMS.contains(c.ecosystem()) ? publicRef(c, version) : null;
					if (!matched.isEmpty()) {
						repoVulnerable++;
						vulnerable.add(vulnerableComponent(name, c.name(), version, matched));
						if (ref != null) {
							vulnerablePackages.add(ref);
						}
					}
					else if (ref != null) {
						otherPackages.add(ref);
					}
				}
			}
			total += repoComponents;
			withoutLicense += repoWithoutLicense;
			LatestSbom first = entry.getValue().getFirst();
			repositories.add(new ReportContent.DependencyRepository(name, first.runNumber(), first.scannedAt(),
					entry.getValue().stream().map(LatestSbom::folder).filter(f -> !f.isEmpty()).toList(), repoComponents,
					repoVulnerable, repoWithoutLicense, images.get(name)));
		}
		vulnerablePackages.stream().limit(AI_PACKAGES).forEach(sbomPackages::add);
		otherPackages.stream().filter(p -> !vulnerablePackages.contains(p)).limit(Math.max(0, AI_PACKAGES - sbomPackages.size()))
			.forEach(sbomPackages::add);
		List<String> withoutSbom = projects.findAll(report.getOrganizationId()).stream()
			.map(ProjectSummary::name)
			.filter(n -> (repository == null || n.equals(repository)) && !byRepository.containsKey(n))
			.sorted()
			.toList();
		int vulnerableCount = vulnerable.size();
		vulnerable.sort(Comparator.comparingInt((ReportContent.VulnerableComponent v) -> Severity.valueOf(v.severity().toUpperCase(Locale.ROOT)).ordinal())
			.thenComparing(Comparator.comparingInt(ReportContent.VulnerableComponent::vulnerabilities).reversed()));
		return new ReportContent.Dependencies(total, unique.size(), vulnerableCount, withoutLicense, sortedDesc(ecosystems, Integer.MAX_VALUE),
				sortedDesc(licenses, LICENSES).entrySet().stream().map(e -> new ReportContent.LicenseCount(e.getKey(), e.getValue())).toList(),
				repositories, vulnerable.size() > VULNERABLE_COMPONENTS ? List.copyOf(vulnerable.subList(0, VULNERABLE_COMPONENTS)) : vulnerable,
				withoutSbom, null, null);
	}

	/** {@code ecosystem:name@version} (Maven: {@code maven:group:artifact@version}), or null when a part is not identifier-like. */
	private static String publicRef(SbomDigest.Component c, String version) {
		String name = c.group() != null ? c.group() + ":" + c.name() : c.name();
		String ref = ClaudeClient.packageRef(name, version.isEmpty() ? null : version, null);
		return ref == null ? null : c.ecosystem() + ":" + ref;
	}

	private static ReportContent.VulnerableComponent vulnerableComponent(String repository, String name, String version,
			List<FindingSummary> matched) {
		FindingSummary worst = matched.stream().min(Comparator.comparingInt(f -> f.severity().ordinal())).orElseThrow();
		String fixed = !blank(worst.fixedVersion()) ? worst.fixedVersion()
				: matched.stream().map(FindingSummary::fixedVersion).filter(v -> !blank(v)).findFirst().orElse(null);
		List<String> ids = matched.stream()
			.map(f -> f.cve() != null ? f.cve() : f.ruleId())
			.filter(id -> !blank(id))
			.distinct()
			.limit(IDS_PER_COMPONENT)
			.toList();
		return new ReportContent.VulnerableComponent(repository, name, version, fixed, worst.severity().wireValue(),
				matched.size(), ids);
	}

	private static String componentKey(String repository, String packageName, String version) {
		return repository + "|" + packageName.toLowerCase(Locale.ROOT) + "|" + version;
	}

	private static Map<String, Integer> sortedDesc(Map<String, Integer> counts, int limit) {
		Map<String, Integer> sorted = new LinkedHashMap<>();
		counts.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.limit(limit)
			.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		return sorted;
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
