package io.github.artemiscore.artemisappsec.finding.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.finding.domain.Finding;
import io.github.artemiscore.artemisappsec.finding.domain.FindingEvent;
import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.finding.persistence.FindingRepository;
import io.github.artemiscore.artemisappsec.finding.persistence.FindingSpecifications;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class FindingServiceImpl implements FindingService {

	private static final List<FindingStatus> ACTIVE = List.of(FindingStatus.OPEN, FindingStatus.TRIAGED);

	private static final List<FindingStatus> NOT_GATING = List.of(FindingStatus.ACCEPTED, FindingStatus.FALSE_POSITIVE);
	private static final int MAX_ACCEPTANCE_YEARS = 1;
	private static final int MIN_JUSTIFICATION = 10;

	private final FindingRepository findings;
	private final ProjectService projects;
	private final FindingPolicy policy;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	FindingServiceImpl(FindingRepository findings, ProjectService projects, FindingPolicy policy,
			ApplicationEventPublisher events) {
		this.findings = findings;
		this.projects = projects;
		this.policy = policy;
		this.events = events;
		this.clock = Clock.systemUTC();
	}

	@Override
	public List<FindingSummary> search(UUID organizationId, FindingQuery query) {
		Map<UUID, ProjectSummary> projectsById = projectsById(organizationId);
		if (projectsById.isEmpty()) {
			return List.of();
		}
		Specification<Finding> spec = FindingSpecifications.inProjects(projectsById.keySet());
		if (query.text() != null && !query.text().isBlank()) {
			spec = spec.and(FindingSpecifications.matchesText(query.text().trim()));
		}
		if (query.kind() != null) {
			spec = spec.and(FindingSpecifications.hasKind(query.kind()));
		}
		if (query.severity() != null) {
			spec = spec.and(FindingSpecifications.hasSeverity(query.severity()));
		}
		if (query.status() != null) {
			spec = spec.and(FindingSpecifications.hasStatus(query.status()));
		}
		if (query.repository() != null && !query.repository().isBlank()) {
			// A removed repository may share its name with the one connected again; the connected one wins.
			UUID projectId = projectsById.values().stream()
				.filter(p -> p.name().equals(query.repository()))
				.sorted(Comparator.comparing(ProjectSummary::removed))
				.map(ProjectSummary::id)
				.findFirst()
				.orElse(null);
			if (projectId == null) {
				return List.of();
			}
			spec = spec.and(FindingSpecifications.inProject(projectId));
		}
		Map<Severity, Integer> sla = policy.remediationDays(organizationId);
		return findings.findAll(spec, sort(query)).stream().map(f -> toSummary(f, projectsById, sla)).toList();
	}

	@Override
	public FindingDetails get(UUID organizationId, UUID id) {
		Map<UUID, ProjectSummary> projectsById = projectsById(organizationId);
		return toDetails(load(id, projectsById), projectsById, policy.remediationDays(organizationId));
	}

	@Override
	@Transactional
	public FindingDetails changeStatus(UUID organizationId, UUID id, ChangeStatusCommand command, String actor) {
		Map<UUID, ProjectSummary> projectsById = projectsById(organizationId);
		Finding finding = load(id, projectsById);
		String note = command.note() == null || command.note().isBlank() ? null : command.note().trim();
		boolean accepting = command.status() == FindingStatus.ACCEPTED;
		if (accepting) {
			requireAcceptance(note, command.expiresAt());
		}
		apply(organizationId, finding, command.status(), note, command.expiresAt(), actor);
		return toDetails(finding, projectsById, policy.remediationDays(organizationId));
	}

	@Override
	@Transactional
	public BulkStatusResult changeStatuses(UUID organizationId, BulkStatusCommand command, String actor) {
		String note = command.note() == null || command.note().isBlank() ? null : command.note().trim();
		if (command.status() == FindingStatus.ACCEPTED) {
			requireAcceptance(note, command.expiresAt());
		}
		Map<UUID, ProjectSummary> projectsById = projectsById(organizationId);
		Set<UUID> ids = new LinkedHashSet<>(command.ids());
		int updated = 0;
		for (UUID id : ids) {
			Finding finding = load(id, projectsById);
			if (finding.getStatus() != command.status() && finding.getStatus().canTransitionTo(command.status())) {
				apply(organizationId, finding, command.status(), note, command.expiresAt(), actor);
				updated++;
			}
		}
		return new BulkStatusResult(updated, ids.size() - updated);
	}

	private void apply(UUID organizationId, Finding finding, FindingStatus target, String note, LocalDate expiresAt,
			String actor) {
		boolean wasAccepted = finding.getStatus() == FindingStatus.ACCEPTED;
		finding.changeStatus(target, note, Instant.now(clock));
		if (wasAccepted) {
			events.publishEvent(new RiskAcceptanceEnded(finding.getId()));
		}
		if (target == FindingStatus.ACCEPTED) {
			events.publishEvent(new RiskAccepted(organizationId, finding.getId(), finding.getProjectId(), finding.getCve(),
					note, expiresAt, actor));
		}
	}

	private void requireAcceptance(String justification, LocalDate expiresAt) {
		if (justification == null || justification.length() < MIN_JUSTIFICATION) {
			throw new IllegalArgumentException(
					"Accepting a risk needs a justification of at least %d characters".formatted(MIN_JUSTIFICATION));
		}
		LocalDate today = LocalDate.now(clock);
		if (expiresAt == null || !expiresAt.isAfter(today)) {
			throw new IllegalArgumentException("Accepting a risk needs an expiry date in the future");
		}
		if (expiresAt.isAfter(today.plusYears(MAX_ACCEPTANCE_YEARS))) {
			throw new IllegalArgumentException("A risk can be accepted for at most %d year".formatted(MAX_ACCEPTANCE_YEARS));
		}
	}

	@Override
	public List<FindingSummary> findActive(UUID organizationId) {
		Map<UUID, ProjectSummary> projectsById = projects.findAll(organizationId).stream()
			.collect(Collectors.toMap(ProjectSummary::id, p -> p));
		if (projectsById.isEmpty()) {
			return List.of();
		}
		Map<Severity, Integer> sla = policy.remediationDays(organizationId);
		return findings.findByProjectIdInAndStatusIn(projectsById.keySet(), ACTIVE).stream()
			.map(f -> toSummary(f, projectsById, sla))
			.sorted(Comparator.comparing(FindingSummary::detectedAt).reversed())
			.toList();
	}

	@Override
	public FindingTrend trend(UUID organizationId, LocalDate from, LocalDate to, ZoneId zone) {
		Set<UUID> projectIds = projectsById(organizationId).keySet();
		Map<LocalDate, long[]> byDay = new TreeMap<>();
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			byDay.put(day, new long[6]);
		}
		if (projectIds.isEmpty()) {
			return new FindingTrend(toDays(byDay), null, 0);
		}
		Instant since = from.atStartOfDay(zone).toInstant();
		for (Object[] row : findings.dailyOpenBySeverity(projectIds, from, to, zone.getId())) {
			long[] counts = byDay.get(LocalDate.parse((String) row[0]));
			if (counts != null) {
				counts[Severity.valueOf((String) row[1]).ordinal()] = ((Number) row[2]).longValue();
			}
		}
		for (Object[] row : findings.dailyDetectedAndFixed(projectIds, since, zone.getId())) {
			long[] counts = byDay.get(LocalDate.parse((String) row[0]));
			if (counts != null) {
				counts[4] = ((Number) row[1]).longValue();
				counts[5] = ((Number) row[2]).longValue();
			}
		}
		Object[] remediation = findings.remediationSince(projectIds, since).getFirst();
		long fixes = ((Number) remediation[0]).longValue();
		Double meanDays = remediation[1] == null ? null
				: Math.round(((Number) remediation[1]).doubleValue() / 86_400 * 10) / 10.0;
		return new FindingTrend(toDays(byDay), meanDays, fixes);
	}

	private static List<FindingTrend.Day> toDays(Map<LocalDate, long[]> byDay) {
		return byDay.entrySet().stream()
			.map(e -> {
				long[] c = e.getValue();
				return new FindingTrend.Day(e.getKey(), c[0], c[1], c[2], c[3], c[4], c[5]);
			})
			.toList();
	}

	private Finding load(UUID id, Map<UUID, ProjectSummary> organizationProjects) {
		return findings.findWithEventsById(id)
			.filter(f -> organizationProjects.containsKey(f.getProjectId()))
			.orElseThrow(() -> new ResourceNotFoundException("Finding", id));
	}

	private static Sort sort(FindingQuery query) {
		Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
		Sort primary = query.sort() == FindingQuery.SortField.CVSS
				? Sort.by(new Sort.Order(direction, "cvssScore").nullsLast())
				: Sort.by(direction, "detectedAt");
		return primary.and(Sort.by("reference"));
	}

	/** Includes removed repositories: their findings stay visible as history. */
	private Map<UUID, ProjectSummary> projectsById(UUID organizationId) {
		return projects.findAllIncludingRemoved(organizationId).stream().collect(Collectors.toMap(ProjectSummary::id, p -> p));
	}

	private FindingDetails toDetails(Finding finding, Map<UUID, ProjectSummary> projectsById, Map<Severity, Integer> sla) {
		List<FindingSummary> related = List.of();
		if (finding.getCve() != null) {
			Specification<Finding> sameCve = FindingSpecifications.inProjects(projectsById.keySet())
				.and((root, q, cb) -> cb.and(cb.equal(root.get("cve"), finding.getCve()),
						cb.notEqual(root.get("id"), finding.getId())));
			related = findings.findAll(sameCve).stream().map(f -> toSummary(f, projectsById, sla)).toList();
		}
		List<FindingEventView> timeline = finding.getTimeline().stream().map(FindingServiceImpl::toEventView).toList();
		return new FindingDetails(toSummary(finding, projectsById, sla), finding.getDescription(), finding.getCvssVector(),
				finding.getCwe(), timeline, related);
	}

	private static FindingEventView toEventView(FindingEvent e) {
		return new FindingEventView(e.getType(), e.getStatus(), e.getNote(), e.getOccurredAt());
	}

	private FindingSummary toSummary(Finding f, Map<UUID, ProjectSummary> projectsById, Map<Severity, Integer> sla) {
		ProjectSummary project = projectsById.get(f.getProjectId());
		Integer days = sla.get(f.getSeverity());
		// The remediation deadline counts from the first detection; it only matters while the finding is active.
		Instant dueAt = days == null ? null : f.getDetectedAt().plus(Duration.ofDays(days));
		boolean overdue = dueAt != null && f.getStatus().isActive() && Instant.now(clock).isAfter(dueAt);
		Double cvss = f.getCvssScore() == null ? null : f.getCvssScore().doubleValue();
		FindingSummary.Location location = f.getLocationPath() == null ? null
				: new FindingSummary.Location(f.getLocationPath(), f.getLocationLine());
		return new FindingSummary(f.getId(), f.getReference(), f.getKind(), f.getRuleId(), f.getCve(),
				project == null ? null : project.name(), project != null && project.removed(), f.getPackageName(), f.getPackageGroup(), f.getInstalledVersion(),
				f.getFixedVersion(), f.getEcosystem(), f.getTitle(), cvss, f.getSeverity(), f.getStatus(), f.getStage(),
				f.getTool(), location, f.getDetectedAt(), f.getStatusChangedAt(), dueAt, overdue);
	}

	@Override
	@Transactional
	public IngestionResult recordDetections(UUID projectId, Stage stage, String tool, List<DetectedIssue> issues) {
		Instant now = Instant.now(clock);
		UUID organizationId = projects.findById(projectId).map(ProjectSummary::organizationId).orElse(null);
		Map<String, Finding.Detection> reported = new LinkedHashMap<>();
		for (DetectedIssue reportedIssue : issues) {
			DetectedIssue issue = applyLicensePolicy(organizationId, reportedIssue);
			if (issue == null) {
				continue;
			}
			Finding.Detection detection = toDetection(issue);
			reported.putIfAbsent(FindingFingerprint.of(detection), detection);
		}
		Map<String, Finding> known = reported.isEmpty() ? Map.of()
				: findings.findByProjectIdAndFingerprintIn(projectId, reported.keySet()).stream()
					.collect(Collectors.toMap(Finding::getFingerprint, f -> f));

		int created = 0;
		int reopened = 0;
		Severity highest = null;
		for (Map.Entry<String, Finding.Detection> entry : reported.entrySet()) {
			Finding existing = known.get(entry.getKey());
			Finding finding;
			if (existing == null) {
				String reference = "F-" + findings.nextReferenceNumber();
				finding = findings.save(Finding.detected(reference, projectId, entry.getKey(), stage, tool, entry.getValue(), now));
				created++;
			} else {
				boolean wasFixed = existing.getStatus() == FindingStatus.FIXED;
				existing.detectedAgain(stage, tool, entry.getValue(), now);
				finding = existing;
				reopened += wasFixed ? 1 : 0;
			}
			// Accepted risks and false positives are reported but never fail a stage.
			if (!NOT_GATING.contains(finding.getStatus())) {
				highest = highest == null || finding.getSeverity().ordinal() < highest.ordinal() ? finding.getSeverity() : highest;
			}
		}

		int fixed = 0;
		for (Finding active : findings.findByProjectIdAndStageAndStatusIn(projectId, stage, ACTIVE)) {
			if (!reported.containsKey(active.getFingerprint())) {
				active.noLongerDetected(tool, now);
				fixed++;
			}
		}
		return new IngestionResult(reported.size(), created, reopened, fixed, highest);
	}

	/** Allowed licenses are dropped, denied ones become high severity, the rest medium (to review). */
	private DetectedIssue applyLicensePolicy(UUID organizationId, DetectedIssue issue) {
		if (issue.kind() != FindingKind.LICENSE || organizationId == null || issue.ruleId() == null) {
			return issue;
		}
		String licenseId = issue.ruleId().startsWith("license:") ? issue.ruleId().substring("license:".length()) : issue.ruleId();
		Severity severity = switch (policy.license(organizationId, licenseId.trim())) {
			case ALLOW -> null;
			case DENY -> Severity.HIGH;
			case REVIEW -> Severity.MEDIUM;
		};
		if (severity == null) {
			return null;
		}
		return new DetectedIssue(issue.kind(), issue.ruleId(), issue.title(), issue.description(), severity, issue.cve(),
				issue.cvssScore(), issue.cvssVector(), issue.cwe(), issue.packageName(), issue.packageVersion(),
				issue.fixedVersion(), issue.ecosystem(), issue.locationPath(), issue.locationLine());
	}

	private static Finding.Detection toDetection(DetectedIssue i) {
		if (i.kind() == null || i.severity() == null || isBlank(i.ruleId()) || isBlank(i.title())) {
			throw new IllegalArgumentException("Each finding needs kind, ruleId, title and severity");
		}
		if (i.kind().isPackageBased() && (isBlank(i.packageName()) || isBlank(i.packageVersion()))) {
			throw new IllegalArgumentException("Finding '%s' of kind %s needs package name and version"
				.formatted(i.ruleId(), i.kind().wireValue()));
		}
		if (!i.kind().isPackageBased() && isBlank(i.locationPath())) {
			throw new IllegalArgumentException("Finding '%s' of kind %s needs a location path"
				.formatted(i.ruleId(), i.kind().wireValue()));
		}
		return new Finding.Detection(i.kind(), i.ruleId().trim(), i.title().trim(), i.description(), i.severity(),
				blankToNull(i.cve()), i.cvssScore(), blankToNull(i.cvssVector()), blankToNull(i.cwe()),
				blankToNull(i.packageName()), blankToNull(i.packageVersion()), blankToNull(i.fixedVersion()),
				blankToNull(i.ecosystem()), blankToNull(i.locationPath()), i.locationLine());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}
}
