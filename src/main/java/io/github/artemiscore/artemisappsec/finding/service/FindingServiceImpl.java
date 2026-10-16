package io.github.artemiscore.artemisappsec.finding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.finding.domain.Finding;
import io.github.artemiscore.artemisappsec.finding.domain.FindingEvent;
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

	private final FindingRepository findings;
	private final ProjectService projects;
	private final Clock clock;

	FindingServiceImpl(FindingRepository findings, ProjectService projects) {
		this.findings = findings;
		this.projects = projects;
		this.clock = Clock.systemUTC();
	}

	@Override
	public List<FindingSummary> search(FindingQuery query) {
		Map<UUID, ProjectSummary> projectsById = projectsById();
		Specification<Finding> spec = Specification.unrestricted();
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
			UUID projectId = projectsById.values().stream()
				.filter(p -> p.name().equals(query.repository()))
				.map(ProjectSummary::id)
				.findFirst()
				.orElse(null);
			if (projectId == null) {
				return List.of();
			}
			spec = spec.and(FindingSpecifications.inProject(projectId));
		}
		return findings.findAll(spec, sort(query)).stream().map(f -> toSummary(f, projectsById)).toList();
	}

	@Override
	public FindingDetails get(UUID id) {
		return toDetails(load(id));
	}

	@Override
	@Transactional
	public FindingDetails changeStatus(UUID id, ChangeStatusCommand command) {
		Finding finding = load(id);
		String note = command.note() == null || command.note().isBlank() ? null : command.note().trim();
		finding.changeStatus(command.status(), note, Instant.now(clock));
		return toDetails(finding);
	}

	@Override
	public List<FindingSummary> findActive() {
		Map<UUID, ProjectSummary> projectsById = projectsById();
		return findings.findByStatusIn(ACTIVE).stream()
			.map(f -> toSummary(f, projectsById))
			.sorted(Comparator.comparing(FindingSummary::detectedAt).reversed())
			.toList();
	}

	private Finding load(UUID id) {
		return findings.findWithEventsById(id).orElseThrow(() -> new ResourceNotFoundException("Finding", id));
	}

	private static Sort sort(FindingQuery query) {
		Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
		Sort primary = query.sort() == FindingQuery.SortField.CVSS
				? Sort.by(new Sort.Order(direction, "cvssScore").nullsLast())
				: Sort.by(direction, "detectedAt");
		return primary.and(Sort.by("reference"));
	}

	private Map<UUID, ProjectSummary> projectsById() {
		return projects.findAll().stream().collect(Collectors.toMap(ProjectSummary::id, p -> p));
	}

	private FindingDetails toDetails(Finding finding) {
		Map<UUID, ProjectSummary> projectsById = projectsById();
		List<FindingSummary> related = List.of();
		if (finding.getCve() != null) {
			Specification<Finding> sameCve = (root, q, cb) -> cb.and(cb.equal(root.get("cve"), finding.getCve()),
					cb.notEqual(root.get("id"), finding.getId()));
			related = findings.findAll(sameCve).stream().map(f -> toSummary(f, projectsById)).toList();
		}
		List<FindingEventView> timeline = finding.getTimeline().stream().map(FindingServiceImpl::toEventView).toList();
		return new FindingDetails(toSummary(finding, projectsById), finding.getDescription(), finding.getCvssVector(),
				finding.getCwe(), timeline, related);
	}

	private static FindingEventView toEventView(FindingEvent e) {
		return new FindingEventView(e.getType(), e.getStatus(), e.getNote(), e.getOccurredAt());
	}

	private static FindingSummary toSummary(Finding f, Map<UUID, ProjectSummary> projectsById) {
		ProjectSummary project = projectsById.get(f.getProjectId());
		Double cvss = f.getCvssScore() == null ? null : f.getCvssScore().doubleValue();
		FindingSummary.Location location = f.getLocationPath() == null ? null
				: new FindingSummary.Location(f.getLocationPath(), f.getLocationLine());
		return new FindingSummary(f.getId(), f.getReference(), f.getKind(), f.getRuleId(), f.getCve(),
				project == null ? null : project.name(), f.getPackageName(), f.getPackageGroup(), f.getInstalledVersion(),
				f.getFixedVersion(), f.getEcosystem(), f.getTitle(), cvss, f.getSeverity(), f.getStatus(), f.getStage(),
				f.getTool(), location, f.getDetectedAt(), f.getStatusChangedAt());
	}

	@Override
	@Transactional
	public IngestionResult recordDetections(UUID projectId, Stage stage, String tool, List<DetectedIssue> issues) {
		Instant now = Instant.now(clock);
		Map<String, Finding.Detection> reported = new LinkedHashMap<>();
		for (DetectedIssue issue : issues) {
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
			highest = highest == null || finding.getSeverity().ordinal() < highest.ordinal() ? finding.getSeverity() : highest;
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
